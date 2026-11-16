package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeactivatedClassroomReleaser.apply")
class DeactivatedClassroomReleaserApplyTest {

    private static final long R204 = 204L;
    private static final long R205 = 205L;
    private static final String NOTE = "Aula deshabilitada";

    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationWriter writer;
    @Mock
    private ClassroomAllocationLock classroomLock;

    private DeactivatedClassroomReleaser releaser;
    private DeactivatedClassroomReleaserFixture world;

    @BeforeEach
    void setUp() {
        releaser = new DeactivatedClassroomReleaser(allocationRepository, occurrenceService, classroomService,
                writer, classroomLock);
        world = new DeactivatedClassroomReleaserFixture();
    }

    private DeactivatedClassroomReleaser.DeactivationResult apply(Long... classrooms) {
        world.stub(allocationRepository, occurrenceService, classroomService);
        Map<Long, String> notes = new LinkedHashMap<>();
        for (Long id : classrooms) {
            notes.put(id, NOTE);
        }
        return releaser.apply(notes);
    }

    @Test
    @DisplayName("fusion: el espejo se libera con MIRROR_RETIRED y su aula como anterior; la principal pasa por restore con su origen y no por release")
    void fusionRetiraEspejoYMueveLaPrincipal() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror = world.mirror(2L, principal, 2);
        world.allocate(principal, R204, AllocationSource.SYSACAD);
        world.allocate(mirror, R205);

        DeactivatedClassroomReleaser.DeactivationResult result = apply(R204);

        assertThat(result.releasedClasses()).isZero();
        assertThat(result.mergedClasses()).isEqualTo(1);
        verify(classroomLock).lock(argThat((Collection<Long> ids) -> List.copyOf(ids).equals(List.of(R205))));
        verify(writer).delete(List.of(mirror), NOTE);
        verify(writer).restore(Map.of(principal, R205), NOTE, AllocationSource.SYSACAD);
        verify(occurrenceService).release(List.of(new OccurrenceRelease(2L, R205)), ReleaseReason.MIRROR_RETIRED,
                NOTE);
        verify(occurrenceService, never()).release(anyList(), eq(ReleaseReason.CLASSROOM_DEACTIVATED), anyString());
    }

    @Test
    @DisplayName("principal sin espejo: se borra la asignacion y se libera con CLASSROOM_DEACTIVATED y su aula como anterior, sin restore")
    void principalLiberadaPorBaja() {
        OccurrenceSlotDto principal = world.principal(1L);
        world.allocate(principal, R204);

        DeactivatedClassroomReleaser.DeactivationResult result = apply(R204);

        assertThat(result.releasedClasses()).isEqualTo(1);
        assertThat(result.mergedClasses()).isZero();
        verify(writer).delete(List.of(principal), NOTE);
        verify(occurrenceService).release(List.of(new OccurrenceRelease(1L, R204)),
                ReleaseReason.CLASSROOM_DEACTIVATED, NOTE);
        verify(writer, never()).restore(any(), anyString(), any());
        verify(occurrenceService, never()).release(anyList(), eq(ReleaseReason.MIRROR_RETIRED), anyString());
    }

    @Test
    @DisplayName("sin asignaciones afectadas devuelve (0, 0) y no escribe nada")
    void sinNadaQueHacer() {
        OccurrenceSlotDto principal = world.principal(1L);
        world.allocate(principal, 999L);

        DeactivatedClassroomReleaser.DeactivationResult result = apply(R204);

        assertThat(result).isEqualTo(new DeactivatedClassroomReleaser.DeactivationResult(0, 0));
        verifyNoInteractions(writer, classroomLock);
        verify(occurrenceService, never()).release(anyList(), any(), anyString());
    }
}
