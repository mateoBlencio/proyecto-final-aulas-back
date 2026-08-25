package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests de {@link AllocationServiceImpl}: exercita la orquestación (no la lógica de
 * {@link AllocationTargetResolver}/{@link AllocationWriter}/{@link AllocationValidator}, que
 * están mockeados acá) para los tres verbos ({@code allocate}/{@code reallocate}/{@code deallocate})
 * cruzados con las dos formas de {@link AllocationTarget} y batch-de-1/batch-de-N.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationServiceImpl")
class AllocationServiceImplTest {

    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationComposer composer;
    @Mock
    private AllocationValidator validator;
    @Mock
    private AllocationTargetResolver targetResolver;
    @Mock
    private AllocationWriter writer;

    private AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllocationServiceImpl(allocationRepository, occurrenceService, composer, validator, targetResolver, writer);
    }

    // ---------- allocate ----------

    @Test
    @DisplayName("allocate: target Occurrences, batch de 1, aula libre → crea y valida solapamiento (MANUAL)")
    void allocateOccurrencesTargetBatchDeUnoCrea() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        Allocation saved = allocation(100L, 10L, 5, AllocationSource.MANUAL);
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(List.of(saved));
        when(composer.composeAll(List.of(saved))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(1);
        verify(validator).validateClassroomsAvailable(Set.of(5L));
        ArgumentCaptor<List<AllocationCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(validator).validateNoOverlap(captor.capture());
        assertThat(captor.getValue()).containsExactly(new AllocationCandidate(occ, 5L));
        verify(writer).create(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: target Event, batch de N (una sola occurrence del evento se resuelve en varias) → crea todas")
    void allocateEventTargetBatchDeNCrea() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        OccurrenceSlotDto occ3 = occurrenceSlot(12L, 1L, futureDate(15));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 5, occ2, 5, occ3, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        List<Allocation> saved = List.of(
                allocation(100L, 10L, 5, AllocationSource.MANUAL),
                allocation(101L, 11L, 5, AllocationSource.MANUAL),
                allocation(102L, 12L, 5, AllocationSource.MANUAL));
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(3);
        verify(writer).create(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: múltiples items en el lote (batch de N a nivel comando) → resuelve juntos y crea juntos")
    void allocateMultiplesItemsBatchDeN() {
        AllocationItem item1 = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationItem item2 = new AllocationItem(new AllocationTarget.Occurrences(List.of(11L)), 6L);
        AllocationCommand command = AllocationCommand.manual(List.of(item1, item2), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 2L, futureDate(2));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 5, occ2, 6);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        List<Allocation> saved = List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL), allocation(101L, 11L, 6, AllocationSource.MANUAL));
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(2);
        verify(validator).validateClassroomsAvailable(Set.of(5L, 6L));
    }

    @Test
    @DisplayName("allocate: lote que no resuelve a ninguna occurrence es un no-op (sin validar aulas ni solapamiento)")
    void allocateTargetVacioEsNoOp() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(Map.of());
        when(writer.create(Map.of(), "obs", AllocationSource.MANUAL)).thenReturn(List.of());

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).isEmpty();
        verify(validator, never()).validateClassroomsAvailable(any());
        verify(validator, never()).validateNoOverlap(anyList());
        verify(writer).create(Map.of(), "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: aula no disponible → el validator corta antes de escribir")
    void allocateAulaNoDisponibleNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 999L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 999);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        doThrow(new AllocationConflictException("aula 999 no existe")).when(validator).validateClassroomsAvailable(Set.of(999L));

        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(AllocationConflictException.class);

        verify(writer, never()).create(any(), any(), any());
        verify(validator, never()).validateNoOverlap(anyList());
    }

    @Test
    @DisplayName("allocate: source MANUAL con solapamiento → el validator corta, writer.create nunca se llama (409, nada se escribe)")
    void allocateManualConSolapeNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        doThrow(new ReallocationConflictException(List.of())).when(validator).validateNoOverlap(anyList());

        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(ReallocationConflictException.class);

        verify(writer, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("allocate: source AUTOMATIC no valida solapamiento (ya lo validó el preview contra su propio snapshot)")
    void allocateAutomaticNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.automatic(List.of(item));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        when(writer.create(resolved, null, AllocationSource.AUTOMATIC)).thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.AUTOMATIC)));

        service.allocate(command);

        verify(validator).validateClassroomsAvailable(Set.of(5L));
        verify(validator, never()).validateNoOverlap(anyList());
    }

    @Test
    @DisplayName("allocate: source IMPORTED clampea a null (incluye pasadas) y no valida solapamiento")
    void allocateImportedClampeaNullYNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.imported(List.of(item), "Importado de Excel");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(3));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), isNull())).thenReturn(resolved);
        when(writer.create(resolved, "Importado de Excel", AllocationSource.IMPORTED))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.IMPORTED)));

        service.allocate(command);

        verify(targetResolver).resolveClassroomByOccurrence(command.items(), null);
        verify(validator, never()).validateNoOverlap(anyList());
    }

    @Test
    @DisplayName("allocate: source MANUAL clampea a hoy (no toca el pasado)")
    void allocateManualClampeaHoy() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(Map.of());

        service.allocate(command);

        verify(targetResolver).resolveClassroomByOccurrence(command.items(), LocalDate.now());
    }

    // ---------- reallocate ----------

    @Test
    @DisplayName("reallocate: target Occurrences, batch de 1 → upsert (no create)")
    void reallocateOccurrencesTargetBatchDeUnoHaceUpsert() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "cambio de aula");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        Allocation saved = allocation(900L, 10L, 5, AllocationSource.MANUAL);
        when(writer.upsert(resolved, "cambio de aula", AllocationSource.MANUAL)).thenReturn(List.of(saved));
        when(composer.composeAll(List.of(saved))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).hasSize(1);
        verify(writer).upsert(resolved, "cambio de aula", AllocationSource.MANUAL);
        verify(writer, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("reallocate: target Event, batch de N → upsert de todas las occurrences resueltas")
    void reallocateEventTargetBatchDeN() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 7L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 7, occ2, 7);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        List<Allocation> saved = List.of(allocation(900L, 10L, 7, AllocationSource.MANUAL), allocation(901L, 11L, 7, AllocationSource.MANUAL));
        when(writer.upsert(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).hasSize(2);
        verify(writer).upsert(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("reallocate: lote vacío es un no-op")
    void reallocateTargetVacioEsNoOp() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(Map.of());
        when(writer.upsert(Map.of(), "obs", AllocationSource.MANUAL)).thenReturn(List.of());

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).isEmpty();
        verify(validator, never()).validateClassroomsAvailable(any());
        verify(validator, never()).validateNoOverlap(anyList());
    }

    @Test
    @DisplayName("reallocate: source MANUAL con solapamiento → 409, writer.upsert nunca se llama")
    void reallocateManualConSolapeNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        doThrow(new ReallocationConflictException(List.of())).when(validator).validateNoOverlap(anyList());

        assertThatThrownBy(() -> service.reallocate(command)).isInstanceOf(ReallocationConflictException.class);

        verify(writer, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("reallocate: source AUTOMATIC no valida solapamiento")
    void reallocateAutomaticNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.automatic(List.of(item));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(resolved);
        when(writer.upsert(resolved, null, AllocationSource.AUTOMATIC)).thenReturn(List.of(allocation(900L, 10L, 5, AllocationSource.AUTOMATIC)));

        service.reallocate(command);

        verify(validator, never()).validateNoOverlap(anyList());
    }

    // ---------- deallocate ----------

    @Test
    @DisplayName("deallocate: target Occurrences, batch de 1 → valida no-pasado y libera")
    void deallocateOccurrencesTargetBatchDeUno() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        when(writer.delete(List.of(occ))).thenReturn(List.of(new AllocationWriter.DeallocatedOccurrence(10L, 5L)));

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).containsExactly(new DeallocatedOccurrenceDto(10L, 5L));
        verify(validator).validateNotPast(occ);
        verify(writer).delete(List.of(occ));
    }

    @Test
    @DisplayName("deallocate: target Event, batch de N → valida y libera todas las occurrences resueltas")
    void deallocateEventTargetBatchDeN() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Event(1L)), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ1, occ2));
        when(writer.delete(List.of(occ1, occ2))).thenReturn(List.of(
                new AllocationWriter.DeallocatedOccurrence(10L, 5L),
                new AllocationWriter.DeallocatedOccurrence(11L, 6L)));

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).containsExactly(new DeallocatedOccurrenceDto(10L, 5L), new DeallocatedOccurrenceDto(11L, 6L));
        verify(validator).validateNotPast(occ1);
        verify(validator).validateNotPast(occ2);
    }

    @Test
    @DisplayName("deallocate: occurrence ya pasada → el validator corta, writer.delete nunca se llama")
    void deallocateOcurrenciaPasadaNoLibera() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        doThrow(new AllocationConflictException("ya ocurrió")).when(validator).validateNotPast(occ);

        assertThatThrownBy(() -> service.deallocate(command)).isInstanceOf(AllocationConflictException.class);

        verify(writer, never()).delete(any());
    }

    @Test
    @DisplayName("deallocate: sin occurrences que liberar (occurrences sin asignación se ignoran) → lista vacía")
    void deallocateSinOccurrencesEsNoOp() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of());
        when(writer.delete(List.of())).thenReturn(List.of());

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).isEmpty();
        verifyNoInteractions(validator);
    }

    // ---------- findById / findByDate ----------

    @Test
    @DisplayName("findById: existente → compone el dto")
    void findByIdExistente() {
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(alloc));
        when(composer.compose(alloc)).thenReturn(dummyResponseDto());

        AllocationResponseDto result = service.findById(1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("findById: inexistente → ResourceNotFoundException")
    void findByIdInexistente() {
        when(allocationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByDate: resuelve occurrences de la fecha y compone sus asignaciones")
    void findByDateComponeAsignaciones() {
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, date);
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        when(occurrenceService.findSlotsByDate(date)).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(alloc));
        when(composer.composeAll(List.of(alloc))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.findByDate(date);

        assertThat(result).hasSize(1);
    }

    // ---------- helpers ----------

    private OccurrenceSlotDto occurrenceSlot(long id, long eventId, LocalDate date) {
        return new OccurrenceSlotDto(id, eventId, date, LocalTime.of(8, 0), LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private Allocation allocation(long id, Long occurrenceId, long classroomId, AllocationSource source) {
        return Allocation.builder()
                .id(id)
                .occurrenceId(occurrenceId)
                .classroomId(classroomId)
                .source(source)
                .build();
    }

    private AllocationResponseDto dummyResponseDto() {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, Instant.now(), null, null, null, null);
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ, long classroomId) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ, classroomId);
        return map;
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ1, long classroomId1, OccurrenceSlotDto occ2, long classroomId2) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ1, classroomId1);
        map.put(occ2, classroomId2);
        return map;
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ1, long classroomId1, OccurrenceSlotDto occ2, long classroomId2,
            OccurrenceSlotDto occ3, long classroomId3) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ1, classroomId1);
        map.put(occ2, classroomId2);
        map.put(occ3, classroomId3);
        return map;
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
