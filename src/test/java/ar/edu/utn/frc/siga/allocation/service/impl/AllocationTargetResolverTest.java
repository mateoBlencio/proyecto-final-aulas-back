package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests del target {@code EventRange} de {@link AllocationTargetResolver}: la reasignación
 * temporal (rango con fin) y la permanente ({@code to == null}).
 *
 * <p>El resolver está mockeado en {@code AllocationServiceImplTest}, así que sus reglas propias
 * —qué ocurrencias entran en el rango y cuáles no— no estaban cubiertas por ningún test.
 *
 * <p>{@link AllocationValidator} se mockea salvo {@code validateRange}, que se delega al método
 * real: es la regla que se está probando, no una colaboración.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationTargetResolver — target EventRange")
class AllocationTargetResolverTest {

    private static final long EVENT_ID = 55L;
    private static final long CLASSROOM_ID = 12L;

    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationValidator validator;

    private AllocationTargetResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AllocationTargetResolver(occurrenceService, validator);
    }

    @Test
    @DisplayName("temporal: solo entran las ocurrencias dentro del rango, con ambos bordes inclusive")
    void temporalRecortaAlRango() {
        LocalDate from = LocalDate.now().plusDays(7);
        LocalDate to = from.plusDays(14);
        realValidateRange();
        // findSlotsByEvent ya filtra el borde inferior en la base; acá llegan desde 'from'.
        when(occurrenceService.findSlotsByEvent(EVENT_ID, from)).thenReturn(List.of(
                slot(1L, from),                 // borde inferior: entra
                slot(2L, from.plusDays(7)),     // en el medio: entra
                slot(3L, to),                   // borde superior: entra
                slot(4L, to.plusDays(7))));     // afuera: no entra

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(from, to)), LocalDate.now());

        assertThat(resolved.keySet()).extracting(OccurrenceSlotDto::occurrenceId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("permanente: to == null toma todo lo que devuelve el evento desde 'from'")
    void permanenteTomaTodoDesdeFrom() {
        LocalDate from = LocalDate.now().plusMonths(1);
        realValidateRange();
        when(occurrenceService.findSlotsByEvent(EVENT_ID, from)).thenReturn(List.of(
                slot(1L, from),
                slot(2L, from.plusMonths(2))));

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(from, null)), LocalDate.now());

        assertThat(resolved.keySet()).extracting(OccurrenceSlotDto::occurrenceId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("el clamp del comando se ignora: manda el 'from' del rango, no LocalDate.now()")
    void ignoraElClampDelComando() {
        LocalDate from = LocalDate.now().plusMonths(3);
        realValidateRange();
        when(occurrenceService.findSlotsByEvent(EVENT_ID, from)).thenReturn(List.of(slot(1L, from)));

        resolver.resolveClassroomByOccurrence(List.of(rangeItem(from, null)), LocalDate.now());

        verify(occurrenceService).findSlotsByEvent(EVENT_ID, from);
        verify(occurrenceService, never()).findSlotsByEvent(EVENT_ID, LocalDate.now());
    }

    @Test
    @DisplayName("'from' en el pasado → 400 y no se consulta nada (D4)")
    void fromPasadoEsBadRequest() {
        realValidateRange();

        assertThatThrownBy(() -> resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(LocalDate.now().minusDays(1), null)), LocalDate.now()))
                .isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(occurrenceService);
    }

    @Test
    @DisplayName("'to' anterior a 'from' → 400 y no se consulta nada")
    void toAntesQueFromEsBadRequest() {
        LocalDate from = LocalDate.now().plusDays(10);
        realValidateRange();

        assertThatThrownBy(() -> resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(from, from.minusDays(1))), LocalDate.now()))
                .isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(occurrenceService);
    }

    @Test
    @DisplayName("rango que arranca hoy con una clase que ya empezó → 409 sobre esa ocurrencia")
    void rangoQueArrancaHoyConClaseYaEmpezada() {
        LocalDate today = LocalDate.now();
        realValidateRange();
        OccurrenceSlotDto yaEmpezo = new OccurrenceSlotDto(
                1L, EVENT_ID, today, LocalTime.MIN, LocalTime.MIN.plusHours(2), OccurrenceStatus.NEEDS_ROOM, 30);
        when(occurrenceService.findSlotsByEvent(EVENT_ID, today)).thenReturn(List.of(yaEmpezo));
        doThrowConflict(yaEmpezo);

        assertThatThrownBy(() -> resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(today, null)), today))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("rango sin ocurrencias en la ventana es un no-op, no un error")
    void rangoVacioEsNoOp() {
        LocalDate from = LocalDate.now().plusDays(3);
        realValidateRange();
        when(occurrenceService.findSlotsByEvent(EVENT_ID, from)).thenReturn(List.of());

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(rangeItem(from, from.plusDays(1))), LocalDate.now());

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("target Event: un lote con varios items Event hace una sola llamada bulk y da el mismo resultado que el camino por evento")
    void loteConVariosEventTargetsHaceUnaSolaLlamada() {
        LocalDate d = LocalDate.now().plusDays(30);
        OccurrenceSlotDto s55 = slotForEvent(1L, 55L, d);
        OccurrenceSlotDto s56 = slotForEvent(2L, 56L, d);
        OccurrenceSlotDto s57 = slotForEvent(3L, 57L, d);
        when(occurrenceService.findSlotsByEvents(anyCollection())).thenReturn(List.of(s55, s56, s57));

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(eventItem(55L, 100L), eventItem(56L, 200L), eventItem(57L, 300L)), null);

        assertThat(resolved).containsExactly(
                org.assertj.core.api.Assertions.entry(s55, 100L),
                org.assertj.core.api.Assertions.entry(s56, 200L),
                org.assertj.core.api.Assertions.entry(s57, 300L));
        verify(occurrenceService, times(1)).findSlotsByEvents(anyCollection());
        verify(occurrenceService, never()).findSlotsByEvent(any(), any());
    }

    @Test
    @DisplayName("target Event: con clamp no nulo se descartan las ocurrencias pasadas")
    void eventDescartaPasadasConClamp() {
        LocalDate today = LocalDate.now();
        OccurrenceSlotDto pasada = new OccurrenceSlotDto(
                1L, 55L, today, LocalTime.MIN, LocalTime.MIN.plusHours(1), OccurrenceStatus.NEEDS_ROOM, 30);
        OccurrenceSlotDto futura = slotForEvent(2L, 55L, today.plusDays(10));
        when(occurrenceService.findSlotsByEvents(anyCollection(), any())).thenReturn(List.of(pasada, futura));

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(eventItem(55L, 100L)), today);

        assertThat(resolved.keySet()).extracting(OccurrenceSlotDto::occurrenceId).containsExactly(2L);
    }

    @Test
    @DisplayName("target Event: dos items apuntando a la misma ocurrencia con aula distinta → 409")
    void dosEventItemsMismaOcurrenciaAulaDistinta() {
        LocalDate d = LocalDate.now().plusDays(30);
        OccurrenceSlotDto shared = slotForEvent(1L, 55L, d);
        when(occurrenceService.findSlotsByEvents(anyCollection())).thenReturn(List.of(shared));

        assertThatThrownBy(() -> resolver.resolveClassroomByOccurrence(
                List.of(eventItem(55L, 100L), eventItem(55L, 200L)), null))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("target Event: dos items idénticos (misma ocurrencia, misma aula) no son conflicto")
    void dosEventItemsMismaOcurrenciaMismaAula() {
        LocalDate d = LocalDate.now().plusDays(30);
        OccurrenceSlotDto shared = slotForEvent(1L, 55L, d);
        when(occurrenceService.findSlotsByEvents(anyCollection())).thenReturn(List.of(shared));

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(eventItem(55L, 100L), eventItem(55L, 100L)), null);

        assertThat(resolved).containsExactly(
                org.assertj.core.api.Assertions.entry(shared, 100L));
    }

    @Test
    @DisplayName("target Occurrences: devuelve lo que trae findSlots y no toca el bulk por evento")
    void occurrencesNoUsaBulkPorEvento() {
        OccurrenceSlotDto a = slotForEvent(1L, 55L, LocalDate.now().plusDays(5));
        OccurrenceSlotDto b = slotForEvent(2L, 55L, LocalDate.now().plusDays(6));
        when(occurrenceService.findSlots(List.of(1L, 2L))).thenReturn(List.of(a, b));

        Map<OccurrenceSlotDto, Long> resolved = resolver.resolveClassroomByOccurrence(
                List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(1L, 2L)), CLASSROOM_ID)),
                LocalDate.now());

        assertThat(resolved.keySet()).extracting(OccurrenceSlotDto::occurrenceId).containsExactly(1L, 2L);
        verify(occurrenceService, never()).findSlotsByEvents(anyCollection());
        verify(occurrenceService, never()).findSlotsByEvents(anyCollection(), any());
    }

    // ---------- helpers ----------

    private static AllocationItem eventItem(long eventId, long classroomId) {
        return new AllocationItem(new AllocationTarget.Event(eventId), classroomId);
    }

    private static OccurrenceSlotDto slotForEvent(long occurrenceId, long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(8, 0), LocalTime.of(10, 0),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }

    /** Delega {@code validateRange} al método real: es la regla bajo prueba, no una colaboración. */
    private void realValidateRange() {
        doCallRealMethod().when(validator).validateRange(any(), any());
    }

    private void doThrowConflict(OccurrenceSlotDto occurrence) {
        org.mockito.Mockito.doThrow(new AllocationConflictException("ya pasó"))
                .when(validator).validateNotPast(occurrence);
    }

    private static AllocationItem rangeItem(LocalDate from, LocalDate to) {
        return new AllocationItem(new AllocationTarget.EventRange(EVENT_ID, from, to), CLASSROOM_ID);
    }

    private static OccurrenceSlotDto slot(Long id, LocalDate date) {
        return new OccurrenceSlotDto(id, EVENT_ID, date, LocalTime.of(8, 0), LocalTime.of(10, 0),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }
}
