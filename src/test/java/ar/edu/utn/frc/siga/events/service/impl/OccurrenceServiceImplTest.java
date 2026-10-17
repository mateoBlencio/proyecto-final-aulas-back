package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.EventTestData;
import ar.edu.utn.frc.siga.events.exception.OccurrenceAlreadyPastException;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceServiceImpl")
class OccurrenceServiceImplTest {

    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private EventScheduleValidator eventScheduleValidator;

    private OccurrenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OccurrenceServiceImpl(occurrenceRepository, eventScheduleValidator);
    }

    private Occurrence occurrence(Long id, OccurrenceStatus status) {
        AcademicEvent event = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30));
        return EventTestData.occurrence(id, event, LocalDate.of(2026, 3, 2), status);
    }

    @Test
    @DisplayName("findDatesByIds devuelve id -> fecha con una sola consulta de proyección, sin cargar entidades")
    void findDatesByIds_mapsProjectionByIdWithOneQuery() {
        OccurrenceRepository.OccurrenceDate first = org.mockito.Mockito.mock(OccurrenceRepository.OccurrenceDate.class);
        OccurrenceRepository.OccurrenceDate second = org.mockito.Mockito.mock(OccurrenceRepository.OccurrenceDate.class);
        when(first.getId()).thenReturn(1L);
        when(first.getDate()).thenReturn(LocalDate.of(2026, 3, 4));
        when(second.getId()).thenReturn(2L);
        when(second.getDate()).thenReturn(LocalDate.of(2026, 12, 31));
        when(occurrenceRepository.findDatesByIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(first, second));

        java.util.Map<Long, LocalDate> dates = service.findDatesByIds(List.of(1L, 2L, 3L));

        assertThat(dates).containsOnly(
                java.util.Map.entry(1L, LocalDate.of(2026, 3, 4)), java.util.Map.entry(2L, LocalDate.of(2026, 12, 31)));
        verify(occurrenceRepository, never()).findAllById(any());
    }


    @Test
    @DisplayName("requestRoom: limpia estado, motivo y nota, conserva el aula anterior y no publica eventos")
    void requestRoomLimpiaMotivoYConservaAulaAnterior() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.ROOM_RELEASED);
        occurrence.setReleaseReason(ReleaseReason.VIRTUAL_CLASS);
        occurrence.setReleaseNote("cursado virtual");
        occurrence.setPreviousClassroomId(7L);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(occurrence));

        service.requestRoom(List.of(10L));

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(occurrence.getReleaseReason()).isNull();
        assertThat(occurrence.getReleaseNote()).isNull();
        assertThat(occurrence.getPreviousClassroomId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("release en lote: escribe estado, motivo, nota y el aula anterior de cada ocurrencia")
    void releaseLoteEscribeMotivoNotaYAulaAnterior() {
        Occurrence first = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        Occurrence second = occurrence(11L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L, 11L))).thenReturn(List.of(first, second));

        service.release(List.of(new OccurrenceRelease(10L, 5L), new OccurrenceRelease(11L, 6L)),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");

        assertThat(first.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(first.getReleaseReason()).isEqualTo(ReleaseReason.VIRTUAL_CLASS);
        assertThat(first.getReleaseNote()).isEqualTo("cursado virtual");
        assertThat(first.getPreviousClassroomId()).isEqualTo(5L);
        assertThat(second.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(second.getPreviousClassroomId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("release en lote: un aula anterior nula no pisa la que la ocurrencia ya tenia guardada")
    void releaseLoteConAulaAnteriorNulaConservaLaExistente() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        occurrence.setPreviousClassroomId(7L);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(occurrence));

        service.release(List.of(new OccurrenceRelease(10L, null)), ReleaseReason.CANCELLED, null);

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(occurrence.getPreviousClassroomId()).isEqualTo(7L);
    }


    @Test
    @DisplayName("release en lote: motivo nulo -> 400 y no toca ninguna ocurrencia")
    void releaseLoteMotivoNulo() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);

        assertThatThrownBy(() -> service.release(List.of(new OccurrenceRelease(10L, null)), null, "nota"))
                .isInstanceOfSatisfying(InvalidSelectionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        verify(occurrenceRepository, never()).findAllById(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("release en lote: OTHER sin nota (nula, vacia o en blanco) -> 400")
    void releaseLoteOtherSinNota(String note) {
        assertThatThrownBy(() -> service.release(List.of(new OccurrenceRelease(10L, null)), ReleaseReason.OTHER, note))
                .isInstanceOfSatisfying(InvalidSelectionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(occurrenceRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("release en lote: OTHER con nota se acepta")
    void releaseLoteOtherConNota() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(occurrence));

        service.release(List.of(new OccurrenceRelease(10L, null)), ReleaseReason.OTHER, "motivo libre");

        assertThat(occurrence.getReleaseReason()).isEqualTo(ReleaseReason.OTHER);
        assertThat(occurrence.getReleaseNote()).isEqualTo("motivo libre");
    }

    @Test
    @DisplayName("release en lote: id inexistente -> 404 y las existentes quedan sin tocar")
    void releaseLoteIdInexistente() {
        Occurrence existing = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L, 99L))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.release(
                List.of(new OccurrenceRelease(10L, null), new OccurrenceRelease(99L, null)),
                ReleaseReason.VIRTUAL_CLASS, null))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(existing.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("release en lote: una ocurrencia pasada -> 409 y ninguna del lote cambia de estado")
    void releaseLotePasadaRechazaTodoElLote() {
        Occurrence future = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        Occurrence past = occurrence(11L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L, 11L))).thenReturn(List.of(future, past));
        org.mockito.Mockito.lenient().doThrow(new OccurrenceAlreadyPastException("ya ocurrió"))
                .when(eventScheduleValidator).validateNotPast(past);

        assertThatThrownBy(() -> service.release(
                List.of(new OccurrenceRelease(10L, 5L), new OccurrenceRelease(11L, 6L)),
                ReleaseReason.CANCELLED, null))
                .isInstanceOfSatisfying(OccurrenceAlreadyPastException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(future.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(future.getReleaseReason()).isNull();
        assertThat(future.getPreviousClassroomId()).isNull();
        assertThat(past.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("requestRoom: ocurrencia pasada → propaga la excepción del validator")
    void requestRoomOcurrenciaPasada() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.ROOM_RELEASED);
        when(occurrenceRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(occurrence));
        doThrow(new OccurrenceAlreadyPastException("ya ocurrió"))
                .when(eventScheduleValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.requestRoom(List.of(10L))).isInstanceOf(OccurrenceAlreadyPastException.class);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
    }

    @Test
    @DisplayName("createSimultaneous: crea count ocurrencias con roomSlot correlativo y mirrorOfOccurrenceId a la principal")
    void createSimultaneousCreaOcurrenciasEnlazadas() {
        Occurrence principal = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(principal));
        when(occurrenceRepository.findByEvent_IdAndDate(1L, LocalDate.of(2026, 3, 2)))
                .thenReturn(List.of(principal));
        when(occurrenceRepository.saveAll(org.mockito.ArgumentMatchers.<List<Occurrence>>any()))
                .thenAnswer(invocation -> {
                    List<Occurrence> toSave = invocation.getArgument(0);
                    List<Occurrence> saved = new java.util.ArrayList<>();
                    long nextId = 100L;
                    for (Occurrence o : toSave) {
                        saved.add(Occurrence.builder()
                                .id(nextId++)
                                .event(o.getEvent())
                                .date(o.getDate())
                                .status(o.getStatus())
                                .roomSlot(o.getRoomSlot())
                                .mirrorOfOccurrenceId(o.getMirrorOfOccurrenceId())
                                .build());
                    }
                    return saved;
                });

        List<Long> created = service.createSimultaneous(10L, 2);

        assertThat(created).containsExactly(100L, 101L);
        ArgumentCaptor<List<Occurrence>> captor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Occurrence::getRoomSlot).containsExactly(2, 3);
        assertThat(captor.getValue()).extracting(Occurrence::getMirrorOfOccurrenceId).containsExactly(10L, 10L);
        assertThat(captor.getValue()).extracting(Occurrence::getDate).containsExactly(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));
        assertThat(captor.getValue()).extracting(Occurrence::getStatus).containsExactly(
                OccurrenceStatus.NEEDS_ROOM, OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("createSimultaneous: si ya hay ocurrencias simultáneas, sigue la numeración de roomSlot")
    void createSimultaneousContinuaLaNumeracion() {
        Occurrence principal = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        Occurrence existingMirror = Occurrence.builder()
                .id(11L).event(principal.getEvent()).date(principal.getDate())
                .status(OccurrenceStatus.NEEDS_ROOM).roomSlot(2).mirrorOfOccurrenceId(10L).build();
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(principal));
        when(occurrenceRepository.findByEvent_IdAndDate(1L, LocalDate.of(2026, 3, 2)))
                .thenReturn(List.of(principal, existingMirror));
        when(occurrenceRepository.saveAll(org.mockito.ArgumentMatchers.<List<Occurrence>>any()))
                .thenAnswer(invocation -> {
                    List<Occurrence> toSave = invocation.getArgument(0);
                    List<Occurrence> saved = new java.util.ArrayList<>();
                    long nextId = 200L;
                    for (Occurrence o : toSave) {
                        saved.add(Occurrence.builder().id(nextId++).event(o.getEvent()).date(o.getDate())
                                .status(o.getStatus()).roomSlot(o.getRoomSlot())
                                .mirrorOfOccurrenceId(o.getMirrorOfOccurrenceId()).build());
                    }
                    return saved;
                });

        service.createSimultaneous(10L, 1);

        ArgumentCaptor<List<Occurrence>> captor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Occurrence::getRoomSlot).containsExactly(3);
    }

    @Test
    @DisplayName("findSlot: ocurrencia inexistente → ResourceNotFoundException")
    void findSlotInexistente() {
        when(occurrenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSlot(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findSlot: mapea evento, fechas y horario a partir de la ocurrencia")
    void findSlotMapeaCorrectamente() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));

        var slot = service.findSlot(10L);

        assertThat(slot.occurrenceId()).isEqualTo(10L);
        assertThat(slot.eventId()).isEqualTo(1L);
        assertThat(slot.date()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(slot.status()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }
}
