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
import java.util.Set;

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

        int affected = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));

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

        int affected = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));

        assertThat(affected).isEqualTo(1);
        assertThat(existing.getClassroomId()).isEqualTo(7L);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(existing.getObservation()).isEqualTo("Actualizado por sync de SysAcad");
        verify(allocationRepository, never()).save(existing);
    }

    @Test
    @DisplayName("ocurrencia con asignación propia en la misma aula → no escribe ni cuenta (segunda corrida idéntica)")
    void doesNotRewriteWhenSysacadOwnedAllocationHasSameClassroom() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = mapOf(occ, 5L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        existing.setObservation("Recuperado de SysAcad");
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int first = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));
        int second = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));

        assertThat(first).isZero();
        assertThat(second).isZero();
        assertThat(existing.getObservation()).isEqualTo("Recuperado de SysAcad");
        verify(allocationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(value = AllocationSource.class, names = {"MANUAL", "IMPORTED", "AUTOMATIC"})
    @DisplayName("ocurrencia con asignación de otro origen (humano o Excel) → no la toca, WARN")
    void skipsWhenExistingAllocationHasForeignSource(AllocationSource foreignSource) {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = mapOf(occ, 7L);
        Allocation existing = allocation(100L, 10L, 5L, foreignSource);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));

        assertThat(affected).isEqualTo(0);
        assertThat(existing.getClassroomId()).isEqualTo(5L);
        assertThat(existing.getSource()).isEqualTo(foreignSource);
        verify(allocationRepository, never()).save(existing);
    }

    @Test
    @DisplayName("clase pasada sin asignación se crea aunque el evento no esté en el set")
    void createsPastOccurrenceWithoutAllocation() {
        OccurrenceSlotDto past = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(2));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of());

        int affected = writer.syncFromSysacad(mapOf(past, 5L), Set.of());

        assertThat(affected).isEqualTo(1);
        verify(allocationRepository).save(org.mockito.ArgumentMatchers.argThat(a -> a.getOccurrenceId().equals(10L)
                && a.getClassroomId().equals(5L) && a.getSource() == AllocationSource.SYSACAD));
    }

    @Test
    @DisplayName("clases pasadas sin tocar: asignación SYSACAD de clase pasada no se actualiza aunque el evento esté en el set")
    void doesNotUpdatePastSysacadAllocationEvenIfEventInSet() {
        OccurrenceSlotDto past = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(2));
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(mapOf(past, 7L), Set.of(1L));

        assertThat(affected).isZero();
        assertThat(existing.getClassroomId()).isEqualTo(5L);
        verify(allocationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("clase futura SYSACAD con el evento fuera del set no se actualiza")
    void doesNotUpdateFutureSysacadAllocationWhenEventNotInSet() {
        OccurrenceSlotDto future = occurrenceSlot(10L, 1L, LocalDate.now().plusDays(3));
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(mapOf(future, 7L), Set.of(2L));

        assertThat(affected).isZero();
        assertThat(existing.getClassroomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("clase futura SYSACAD con el evento en el set se actualiza")
    void updatesFutureSysacadAllocationWhenEventInSet() {
        OccurrenceSlotDto future = occurrenceSlot(10L, 1L, LocalDate.now().plusDays(3));
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(mapOf(future, 7L), Set.of(1L));

        assertThat(affected).isEqualTo(1);
        assertThat(existing.getClassroomId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @EnumSource(value = AllocationSource.class, names = {"MANUAL", "IMPORTED", "AUTOMATIC"})
    @DisplayName("clase futura de otro origen no se toca aunque el evento esté en el set")
    void doesNotUpdateForeignFutureAllocationEvenIfEventInSet(AllocationSource foreignSource) {
        OccurrenceSlotDto future = occurrenceSlot(10L, 1L, LocalDate.now().plusDays(3));
        Allocation existing = allocation(100L, 10L, 5L, foreignSource);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(mapOf(future, 7L), Set.of(1L));

        assertThat(affected).isZero();
        assertThat(existing.getClassroomId()).isEqualTo(5L);
        assertThat(existing.getSource()).isEqualTo(foreignSource);
    }

    @Test
    @DisplayName("lote vacío es un no-op, sin ir a buscar asignaciones existentes")
    void emptyBatchIsNoOp() {
        int affected = writer.syncFromSysacad(Map.of(), Set.of());

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

        int affected = writer.syncFromSysacad(classroomByOccurrence, Set.of(1L));

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
    @DisplayName("restore: persiste el origen SYSACAD pero publica el movimiento como MANUAL")
    void restorePersistsOriginalSourceButPublishesAsManual() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 3L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.MANUAL);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        writer.restore(mapOf(occ, 7L), "reapertura", AllocationSource.SYSACAD);

        assertThat(existing.getClassroomId()).isEqualTo(7L);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(publishedChanges(AllocationSource.MANUAL, "reapertura")).containsExactly(
                new Change(10L, 3L, occ.date(), occ.startTime(), occ.endTime(), 5L, 7L));
        verify(movementPublisher, never()).publish(eq(AllocationSource.SYSACAD), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
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

        assertThat(writer.syncFromSysacad(batch, Set.of(1L))).isEqualTo(2);

        verifyNoInteractions(movementPublisher);
    }

    private List<Change> publishedChanges(AllocationSource source, String reason) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Change>> captor = ArgumentCaptor.forClass(List.class);
        verify(movementPublisher).publish(eq(source), eq(reason), captor.capture());
        return captor.getValue();
    }

    private OccurrenceSlotDto occurrenceSlot(long id, long eventId) {
        return occurrenceSlot(id, eventId, LocalDate.now().plusDays(1));
    }

    private OccurrenceSlotDto occurrenceSlot(long id, long eventId, LocalDate date) {
        return new OccurrenceSlotDto(id, eventId, date, LocalTime.of(8, 0),
                LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 30, 1, null, null, null, null);
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


    @Test
    @DisplayName("overwriteFromSysacad: pisa la asignación de cualquier origen y la deja SYSACAD con la observación recibida")
    void overwriteReplacesAnySourceAndPublishesChanges() {
        OccurrenceSlotDto manual = occurrenceSlot(10L, 1L);
        OccurrenceSlotDto imported = occurrenceSlot(11L, 1L);
        Allocation manualAllocation = allocation(100L, 10L, 5L, AllocationSource.MANUAL);
        Allocation importedAllocation = allocation(101L, 11L, 6L, AllocationSource.IMPORTED);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(manualAllocation, importedAllocation));

        Map<OccurrenceSlotDto, Long> target = new LinkedHashMap<>();
        target.put(manual, 9L);
        target.put(imported, 9L);

        List<Change> changes = writer.overwriteFromSysacad(target, "Cambio directo en SysAcad", "SYSACAD");

        assertThat(manualAllocation.getClassroomId()).isEqualTo(9L);
        assertThat(manualAllocation.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(manualAllocation.getObservation()).isEqualTo("Cambio directo en SysAcad");
        assertThat(importedAllocation.getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(changes).extracting(Change::occurrenceId, Change::fromClassroomId, Change::toClassroomId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 5L, 9L),
                        org.assertj.core.groups.Tuple.tuple(11L, 6L, 9L));
        verify(movementPublisher).publishSysacadDirectChange("SYSACAD", "Cambio directo en SysAcad", changes);
    }

    @Test
    @DisplayName("overwriteFromSysacad: crea la asignación que faltaba con source SYSACAD y desde nulo")
    void overwriteCreatesMissingAllocation() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of());

        List<Change> changes = writer.overwriteFromSysacad(mapOf(occ, 9L), "Cambio directo en SysAcad", "SYSACAD");

        ArgumentCaptor<Allocation> saved = ArgumentCaptor.forClass(Allocation.class);
        verify(allocationRepository).save(saved.capture());
        assertThat(saved.getValue().getClassroomId()).isEqualTo(9L);
        assertThat(saved.getValue().getSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(changes).singleElement().satisfies(change -> assertThat(change.fromClassroomId()).isNull());
    }

    @Test
    @DisplayName("overwriteFromSysacad: una asignación que ya está en esa aula no cambia ni cuenta como movimiento")
    void overwriteSkipsAllocationAlreadyInClassroom() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Allocation existing = allocation(100L, 10L, 9L, AllocationSource.MANUAL);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        List<Change> changes = writer.overwriteFromSysacad(mapOf(occ, 9L), "Cambio directo en SysAcad", "SYSACAD");

        assertThat(changes).isEmpty();
        assertThat(existing.getSource()).isEqualTo(AllocationSource.MANUAL);
        verify(movementPublisher).publishSysacadDirectChange("SYSACAD", "Cambio directo en SysAcad", List.of());
    }

    @Test
    @DisplayName("overwriteFromSysacad: sin ocurrencias no consulta ni publica")
    void overwriteWithoutOccurrencesDoesNothing() {
        assertThat(writer.overwriteFromSysacad(Map.of(), "x", "SYSACAD")).isEmpty();

        verifyNoInteractions(allocationRepository, movementPublisher);
    }

    @Test
    @DisplayName("syncFromSysacad: no actualiza una asignación SYSACAD de un evento que no está en eventIdsToUpdate")
    void syncDoesNotUpdateEventOutsideUpdateSet() {
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L);
        Allocation existing = allocation(100L, 10L, 5L, AllocationSource.SYSACAD);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(existing));

        int affected = writer.syncFromSysacad(mapOf(occ, 7L), Set.of(99L));

        assertThat(affected).isZero();
        assertThat(existing.getClassroomId()).isEqualTo(5L);
    }
}
