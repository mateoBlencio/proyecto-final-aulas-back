package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreCommand;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreResult;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.KeptDateDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RevertedDateDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ReversionDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation.Original;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestOriginalRestorer")
class RoomRequestOriginalRestorerTest {

    private static final LocalDate DATE_1 = LocalDate.of(2027, 3, 9);
    private static final LocalDate DATE_2 = LocalDate.of(2027, 3, 16);

    @Mock
    private AllocationService allocationService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private RoomRequestOccurrenceResolver occurrenceResolver;

    private RoomRequestOriginalRestorer restorer() {
        return new RoomRequestOriginalRestorer(allocationService, occurrenceService, classroomService, occurrenceResolver);
    }

    private static RoomRequestItemAllocation principal(Long occurrenceId, Long classroomId, Long originalId,
            AllocationSource source) {
        return RoomRequestItemAllocation.builder().occurrenceId(occurrenceId).classroomId(classroomId).position(1)
                .originalCaptured(true).originalClassroomId(originalId).originalSource(source).build();
    }

    private static RoomRequestItem itemWith(RoomRequestItemAllocation... rows) {
        return RoomRequestItem.builder().id(1L).allocations(new ArrayList<>(List.of(rows))).build();
    }

    private static ClassroomResponseDto classroom(Long id, int roomNumber) {
        return new ClassroomResponseDto(id, roomNumber, 40, 1L, "Edificio " + id, 1L, "Aula común", null, true);
    }

    @Test
    @DisplayName("restore: manda al allocation solo las filas principales futuras con su aula y origen original")
    void restoreSoloPrincipalesFuturas() {
        RoomRequestItemAllocation future = principal(10L, 200L, 100L, AllocationSource.SYSACAD);
        RoomRequestItemAllocation past = principal(11L, 200L, 101L, AllocationSource.MANUAL);
        RoomRequestItemAllocation mirror = RoomRequestItemAllocation.builder().occurrenceId(12L).classroomId(300L)
                .position(2).build();
        RoomRequestItem item = itemWith(future, past, mirror);
        when(occurrenceResolver.onlyFuture(item, List.of(10L, 11L))).thenReturn(List.of(10L));
        when(allocationService.restoreClassrooms(any())).thenReturn(new RestoreResult(List.of(10L), List.of(), List.of(), java.util.Map.of()));

        RoomRequestOriginalRestorer.Restoration result = restorer().restore(item, "obs", true);

        ArgumentCaptor<RestoreCommand> captor = ArgumentCaptor.forClass(RestoreCommand.class);
        verify(allocationService).restoreClassrooms(captor.capture());
        assertThat(captor.getValue().items())
                .containsExactly(new RestoreCommand.RestoreItem(10L, 100L, AllocationSource.SYSACAD));
        assertThat(captor.getValue().allOrNothing()).isTrue();
        assertThat(captor.getValue().observation()).isEqualTo("obs");
        assertThat(result.restored()).containsExactly(10L);
        assertThat(result.withoutOriginal()).isEmpty();
    }

    @Test
    @DisplayName("restore: una fila capturada sin aula previa va a withoutOriginal y no se restaura a un aula ajena")
    void restoreFechaSinAulaPreviaNoSeRestaura() {
        RoomRequestItemAllocation noPrevious = principal(10L, 200L, null, null);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).previousClassroomId(55L)
                .allocations(new ArrayList<>(List.of(noPrevious))).build();
        when(occurrenceResolver.onlyFuture(item, List.of(10L))).thenReturn(List.of(10L));

        RoomRequestOriginalRestorer.Restoration result = restorer().restore(item, "obs", false);

        assertThat(result.withoutOriginal()).containsExactly(10L);
        assertThat(result.toDeallocate()).containsExactly(10L);
        verify(allocationService, never()).restoreClassrooms(any());
    }

    @Test
    @DisplayName("restore: una fila anterior a V60 usa el aula anterior del ítem con origen MANUAL")
    void restoreFilaPreV60UsaAulaAnterior() {
        RoomRequestItemAllocation legacy = RoomRequestItemAllocation.builder().occurrenceId(10L).classroomId(200L)
                .position(1).originalCaptured(false).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).previousClassroomId(55L)
                .allocations(new ArrayList<>(List.of(legacy))).build();
        when(occurrenceResolver.onlyFuture(item, List.of(10L))).thenReturn(List.of(10L));
        when(allocationService.restoreClassrooms(any())).thenReturn(new RestoreResult(List.of(10L), List.of(), List.of(), java.util.Map.of()));

        restorer().restore(item, "obs", false);

        ArgumentCaptor<RestoreCommand> captor = ArgumentCaptor.forClass(RestoreCommand.class);
        verify(allocationService).restoreClassrooms(captor.capture());
        assertThat(captor.getValue().items())
                .containsExactly(new RestoreCommand.RestoreItem(10L, 55L, AllocationSource.MANUAL));
    }

    @Test
    @DisplayName("restore: una fila pre-V60 sin aula anterior en el ítem queda sin original")
    void restoreFilaPreV60SinAulaAnterior() {
        RoomRequestItemAllocation legacy = RoomRequestItemAllocation.builder().occurrenceId(10L).classroomId(200L)
                .position(1).originalCaptured(false).build();
        RoomRequestItem item = itemWith(legacy);
        when(occurrenceResolver.onlyFuture(item, List.of(10L))).thenReturn(List.of(10L));

        RoomRequestOriginalRestorer.Restoration result = restorer().restore(item, "obs", false);

        assertThat(result.withoutOriginal()).containsExactly(10L);
        verify(allocationService, never()).restoreClassrooms(any());
    }

    @Test
    @DisplayName("toDeallocate: junta las fechas sin original con las de aula original desactivada")
    void toDeallocateJuntaSinOriginalYDesactivadas() {
        RoomRequestOriginalRestorer.Restoration restoration = new RoomRequestOriginalRestorer.Restoration(
                List.of(1L), List.of(), List.of(2L), List.of(3L), java.util.Map.of());

        assertThat(restoration.toDeallocate()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("captureOriginals: devuelve aula y origen actuales por ocurrencia y omite las que no tienen aula")
    void captureOriginalsOmiteSinAula() {
        OccurrenceResponseDto occ10 = new OccurrenceResponseDto(10L, 1L, DATE_1, OccurrenceStatus.NEEDS_ROOM,
                LocalTime.of(8, 0), LocalTime.of(9, 30), null, null, null);
        OccurrenceResponseDto occ11 = new OccurrenceResponseDto(11L, 1L, DATE_2, OccurrenceStatus.NEEDS_ROOM,
                LocalTime.of(8, 0), LocalTime.of(9, 30), null, null, null);
        when(allocationService.findByOccurrenceIds(List.of(10L, 11L))).thenReturn(List.of(
                new AllocationResponseDto(1L, AllocationSource.SYSACAD, Instant.now(), null, occ10, null, classroom(100L, 4), null),
                new AllocationResponseDto(2L, AllocationSource.MANUAL, Instant.now(), null, occ11, null, null, null)));

        var result = restorer().captureOriginals(List.of(10L, 11L));

        assertThat(result).containsOnlyKeys(10L).containsEntry(10L, new Original(100L, AllocationSource.SYSACAD));
    }

    @Test
    @DisplayName("toReversion: separa fechas revertidas y las que quedan, con aula original o nula si no hay")
    void toReversionSeparaRevertidasYQuedan() {
        RoomRequestItemAllocation a = principal(10L, 200L, 100L, AllocationSource.MANUAL);
        RoomRequestItemAllocation b = principal(11L, 200L, 101L, AllocationSource.MANUAL);
        RoomRequestItemAllocation c = principal(12L, 200L, null, null);
        RoomRequestItem item = itemWith(a, b, c);
        OccurrenceConflictDto occupied = new OccurrenceConflictDto(11L, DATE_2, LocalTime.of(8, 0),
                LocalTime.of(9, 30), 101L, 99L, 98L, 97L, 90);
        var restoration = new RoomRequestOriginalRestorer.Restoration(List.of(10L), List.of(occupied), List.of(),
                List.of(12L), java.util.Map.of());
        LocalDate date3 = DATE_2.plusDays(7);
        when(occurrenceService.findSlots(any())).thenReturn(List.of(slot(10L, DATE_1), slot(11L, DATE_2), slot(12L, date3)));
        when(classroomService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(classroom(100L, 4), classroom(101L, 5)));

        ReversionDto reversion = restorer().toReversion(item, restoration);

        assertThat(reversion.reverted()).containsExactly(
                new RevertedDateDto(DATE_1, new ClassroomOptionDto(100L, 4, "Edificio 100")));
        assertThat(reversion.kept()).containsExactly(
                new KeptDateDto(DATE_2, new ClassroomOptionDto(101L, 5, "Edificio 101"), null),
                new KeptDateDto(date3, null, null));
    }

    @Test
    @DisplayName("toReversion: sin nada que informar no consulta ocurrencias ni aulas")
    void toReversionVacio() {
        RoomRequestItem item = itemWith();
        var restoration = new RoomRequestOriginalRestorer.Restoration(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());

        ReversionDto reversion = restorer().toReversion(item, restoration);

        assertThat(reversion.reverted()).isEmpty();
        assertThat(reversion.kept()).isEmpty();
        verifyNoInteractions(occurrenceService, classroomService, allocationService);
    }

    private static OccurrenceSlotDto slot(Long id, LocalDate date) {
        return new OccurrenceSlotDto(id, 1L, date, LocalTime.of(8, 0), LocalTime.of(9, 30),
                OccurrenceStatus.NEEDS_ROOM, 30, 1, null, null, null, null);
    }
}
