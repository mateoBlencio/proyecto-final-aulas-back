package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests de la política de precedencia del sync de SysAcad (ver
 * .claude/docs/plan-sync-eventos-sysacad.md §4), implementada en {@link AllocationWriter#syncFromSysacad}:
 * crea si la ocurrencia no tiene asignación, actualiza si ya era {@code SYSACAD}, no toca (WARN) si es
 * de cualquier otro origen.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationWriter.syncFromSysacad (política de precedencia SysAcad)")
class AllocationWriterTest {

    @Mock
    private AllocationRepository allocationRepository;

    @Mock
    private ClassroomMovementPublisher movementPublisher;

    private AllocationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AllocationWriter(allocationRepository, movementPublisher);
    }

    @Test
    @DisplayName("ocurrencia sin asignación → crea, source=SYSACAD, observación 'Recuperado de SysAcad'")
    void createsWhenNoExistingAllocation() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = mapOf(occ, 5L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of());

        int affected = writer.syncFromSysacad(classroomByOccurrence);

        assertThat(affected).isEqualTo(1);
        ArgumentCaptor<Allocation> captor = ArgumentCaptor.forClass(Allocation.class);
        verify(allocationRepository).save(captor.capture());
        Allocation saved = captor.getValue();
        assertThat(saved.getOccurrenceId()).isEqualTo(10L);
        assertThat(saved.getClassroomId()).isEqualTo(5L);
        assertThat(saved.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(saved.getObservation()).isEqualTo("Recuperado de SysAcad");
    }

    @Test
    @DisplayName("ocurrencia con asignación propia (source=SYSACAD) → actualiza el aula, observación neutra")
    void updatesWhenExistingAllocationIsSysacadOwned() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = mapOf(occ, 7L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(classroomByOccurrence);

        assertThat(affected).isEqualTo(1);
        assertThat(existing.getClassroomId()).isEqualTo(7L);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(existing.getObservation()).isEqualTo("Actualizado por sync de SysAcad");
        verify(allocationRepository, never()).save(existing);
    }

    @ParameterizedTest
    @EnumSource(value = AllocationSource.class, names = {"MANUAL", "IMPORTED", "AUTOMATIC"})
    @DisplayName("ocurrencia con asignación de otro origen (humano o Excel) → no la toca, WARN")
    void skipsWhenExistingAllocationHasForeignSource(AllocationSource foreignSource) {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = mapOf(occ, 7L);
        Allocation existing = allocation(100L, 10L, 5L, foreignSource);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(classroomByOccurrence);

        assertThat(affected).isEqualTo(0);
        assertThat(existing.getClassroomId()).isEqualTo(5L);
        assertThat(existing.getSource()).isEqualTo(foreignSource);
        verify(allocationRepository, never()).save(existing);
    }

    @Test
    @DisplayName("lote vacío es un no-op, sin ir a buscar asignaciones existentes")
    void emptyBatchIsNoOp() {
        int affected = writer.syncFromSysacad(Map.of());

        assertThat(affected).isEqualTo(0);
        verify(allocationRepository, never()).findByOccurrenceIdIn(anyCollection());
    }

    @Test
    @DisplayName("mezcla de create + update-propio + skip-ajeno en el mismo lote: el conteo sólo cuenta lo afectado")
    void mixedBatchCountsOnlyAffected() {
        OccurrenceSlotDto occCreate = occurrenceSlot(10L, 1L);
        OccurrenceSlotDto occUpdate = occurrenceSlot(11L, 1L);
        OccurrenceSlotDto occSkip = occurrenceSlot(12L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = new LinkedHashMap<>();
        classroomByOccurrence.put(occCreate, 5L);
        classroomByOccurrence.put(occUpdate, 6L);
        classroomByOccurrence.put(occSkip, 8L);

        Allocation ownAllocation = allocation(200L, 11L, 1L, AllocationSource.SYSACAD);
        Allocation foreignAllocation = allocation(201L, 12L, 2L, AllocationSource.MANUAL);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(ownAllocation, foreignAllocation));

        int affected = writer.syncFromSysacad(classroomByOccurrence);

        assertThat(affected).isEqualTo(2);
        verify(allocationRepository).save(org.mockito.ArgumentMatchers.argThat(a -> a.getOccurrenceId().equals(10L)));
        assertThat(ownAllocation.getClassroomId()).isEqualTo(6L);
        assertThat(foreignAllocation.getClassroomId()).isEqualTo(2L);
    }

    // ---------- aviso de movimientos ----------

    @Test
    @DisplayName("upsert: aula distinta publica Change(from=anterior, to=nueva) con fecha, horario y evento de la ocurrencia")
    void upsertPublishesChangeWithPreviousClassroom() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 3L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.MANUAL);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        writer.upsert(mapOf(occ, 7L), "obra", AllocationSource.MANUAL);

        assertThat(existing.getClassroomId()).isEqualTo(7L);
        assertThat(publishedChanges(AllocationSource.MANUAL, "obra")).containsExactly(
                new Change(10L, 3L, occ.date(), occ.startTime(), occ.endTime(), 5L, 7L));
    }

    @Test
    @DisplayName("create: ocurrencia sin asignación previa publica Change con from nulo")
    void createPublishesChangeWithNullFrom() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 3L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of());

        writer.create(mapOf(occ, 7L), "alta", AllocationSource.MANUAL);

        assertThat(publishedChanges(AllocationSource.MANUAL, "alta")).extracting(Change::fromClassroomId, Change::toClassroomId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(null, 7L));
    }

    @Test
    @DisplayName("upsert: from == to no genera Change; el publisher recibe lista vacia")
    void upsertWithSameClassroomGeneratesNoChange() {
        OccurrenceSlotDto same = occurrenceSlot(10L, 3L);
        OccurrenceSlotDto moved = occurrenceSlot(11L, 3L);
        Map<OccurrenceSlotDto, Long> batch = new LinkedHashMap<>();
        batch.put(same, 5L);
        batch.put(moved, 8L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L, 11L))).thenReturn(List.of(
                allocation(100L, 10L, 5L, AllocationSource.MANUAL), allocation(101L, 11L, 6L, AllocationSource.MANUAL)));

        writer.upsert(batch, "obs", AllocationSource.MANUAL);

        assertThat(publishedChanges(AllocationSource.MANUAL, "obs")).extracting(Change::occurrenceId)
                .containsExactly(11L);
    }

    @Test
    @DisplayName("upsert: todas las ocurrencias con la misma aula publican lista vacia")
    void upsertWithOnlyUnchangedOccurrencesPublishesEmptyList() {
        OccurrenceSlotDto same = occurrenceSlot(10L, 3L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L)))
                .thenReturn(List.of(allocation(100L, 10L, 5L, AllocationSource.MANUAL)));

        writer.upsert(mapOf(same, 5L), "obs", AllocationSource.MANUAL);

        assertThat(publishedChanges(AllocationSource.MANUAL, "obs")).isEmpty();
    }

    @Test
    @DisplayName("delete: publica Change(from=aula, to=null) con source MANUAL y la observacion")
    void deletePublishesReleaseAsManual() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 3L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.AUTOMATIC);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        writer.delete(List.of(occ), "se rompio el proyector");

        verify(allocationRepository).deleteAll(List.of(existing));
        assertThat(publishedChanges(AllocationSource.MANUAL, "se rompio el proyector")).containsExactly(
                new Change(10L, 3L, occ.date(), occ.startTime(), occ.endTime(), 5L, null));
    }

    @Test
    @DisplayName("delete: sin asignaciones existentes no publica")
    void deleteWithoutExistingAllocationsDoesNotPublish() {
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of());

        writer.delete(List.of(occurrenceSlot(10L, 3L)), "obs");

        verifyNoInteractions(movementPublisher);
    }

    @Test
    @DisplayName("syncFromSysacad: nunca publica movimientos, ni al crear ni al actualizar")
    void syncFromSysacadDoesNotPublish() {
        OccurrenceSlotDto created = occurrenceSlot(10L, 1L);
        OccurrenceSlotDto updated = occurrenceSlot(11L, 1L);
        Map<OccurrenceSlotDto, Long> batch = new LinkedHashMap<>();
        batch.put(created, 5L);
        batch.put(updated, 6L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(allocation(200L, 11L, 1L, AllocationSource.SYSACAD)));

        assertThat(writer.syncFromSysacad(batch)).isEqualTo(2);

        verifyNoInteractions(movementPublisher);
    }

    private List<Change> publishedChanges(AllocationSource source, String reason) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Change>> captor = ArgumentCaptor.forClass(List.class);
        verify(movementPublisher).publish(eq(source), eq(reason), captor.capture());
        return captor.getValue();
    }

    private OccurrenceSlotDto occurrenceSlot(long id, long eventId) {
        return new OccurrenceSlotDto(id, eventId, LocalDate.now().plusDays(1), LocalTime.of(8, 0),
                LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private Allocation allocation(long id, Long occurrenceId, long classroomId, AllocationSource source) {
        return Allocation.builder()
                .id(id)
                .occurrenceId(occurrenceId)
                .classroomId(classroomId)
                .source(source)
                .build();
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ, long classroomId) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ, classroomId);
        return map;
    }
}
