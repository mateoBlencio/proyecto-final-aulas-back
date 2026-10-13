package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.audit.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.service.RevisionReader;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationAuditHistoryServiceImpl")
class AllocationAuditHistoryServiceImplTest {

    private static final long EVENT_ID = 7L;
    private static final long BUILDING_A = 1L;
    private static final long BUILDING_B = 2L;
    private static final long ROOM_IN_A = 10L;
    private static final long ROOM_IN_B = 20L;
    private static final long OTHER_ROOM_IN_B = 21L;

    @Mock
    private RevisionReader revisionReader;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;

    @InjectMocks
    private AllocationAuditHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(occurrenceService.findSlotsByEvent(EVENT_ID, null)).thenReturn(List.of());
    }

    @Test
    @DisplayName("alcance irrestricto: devuelve todas las revisiones sin consultar aulas")
    void alcanceIrrestricto() {
        List<RevisionDto<AllocationHistorySnapshotDto>> all = List.of(
                revision(1, RevisionKind.CREATED, snapshot(1L, ROOM_IN_B)),
                revision(2, RevisionKind.DELETED, null));
        givenRevisions(all);
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_HISTORY_READ)).thenReturn(BuildingScope.unrestricted());

        assertThat(service.findAllocationHistory(EVENT_ID)).isEqualTo(all);

        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("alcance por edificio: oculta las revisiones de asignaciones que nunca tuvieron un aula de su edificio")
    void ocultaAsignacionesAjenas() {
        givenRevisions(List.of(
                revision(1, RevisionKind.CREATED, snapshot(1L, ROOM_IN_A)),
                revision(2, RevisionKind.CREATED, snapshot(2L, ROOM_IN_B)),
                revision(3, RevisionKind.MODIFIED, snapshot(2L, OTHER_ROOM_IN_B))));
        givenScopeOnBuildingA();

        List<Integer> visible = revisionNumbers(service.findAllocationHistory(EVENT_ID));

        assertThat(visible).containsExactly(1);
    }

    @Test
    @DisplayName("alcance por edificio: un cambio de aula se ve si el aula previa o la nueva es de su edificio")
    void cambioDeAulaConUnExtremoEnSuEdificio() {
        givenRevisions(List.of(
                revision(1, RevisionKind.CREATED, snapshot(1L, ROOM_IN_B)),
                revision(2, RevisionKind.MODIFIED, snapshot(1L, ROOM_IN_A)),
                revision(3, RevisionKind.MODIFIED, snapshot(1L, ROOM_IN_B))));
        givenScopeOnBuildingA();

        List<Integer> visible = revisionNumbers(service.findAllocationHistory(EVENT_ID));

        assertThat(visible).containsExactly(2, 3);
    }

    @Test
    @DisplayName("alcance por edificio: la baja se ve si la revisión anterior era un aula de su edificio")
    void bajaDespuesDeAulaPropia() {
        givenRevisions(List.of(
                revision(1, RevisionKind.CREATED, snapshot(1L, ROOM_IN_A)),
                revision(2, RevisionKind.DELETED, null)));
        givenScopeOnBuildingA();

        assertThat(revisionNumbers(service.findAllocationHistory(EVENT_ID))).containsExactly(1, 2);
    }

    @Test
    @DisplayName("alcance por edificio: la baja de una asignación ajena queda oculta")
    void bajaDespuesDeAulaAjena() {
        givenRevisions(List.of(
                revision(1, RevisionKind.CREATED, snapshot(1L, ROOM_IN_B)),
                revision(2, RevisionKind.DELETED, null)));
        givenScopeOnBuildingA();

        assertThat(service.findAllocationHistory(EVENT_ID)).isEmpty();
    }

    private void givenScopeOnBuildingA() {
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_HISTORY_READ))
                .thenReturn(BuildingScope.of(Set.of(BUILDING_A)));
        when(classroomService.findByIdsIncludingDeactivated(anyCollection())).thenReturn(List.of(
                classroom(ROOM_IN_A, BUILDING_A),
                classroom(ROOM_IN_B, BUILDING_B),
                classroom(OTHER_ROOM_IN_B, BUILDING_B)));
    }

    @SuppressWarnings("unchecked")
    private void givenRevisions(List<RevisionDto<AllocationHistorySnapshotDto>> revisions) {
        when(revisionReader.read(eq(Allocation.class), eq("occurrenceId"), anyCollection(), any()))
                .thenReturn((List) revisions);
    }

    private static List<Integer> revisionNumbers(List<RevisionDto<AllocationHistorySnapshotDto>> revisions) {
        return revisions.stream().map(RevisionDto::revision).toList();
    }

    private static RevisionDto<AllocationHistorySnapshotDto> revision(
            int number, RevisionKind kind, AllocationHistorySnapshotDto snapshot) {
        return new RevisionDto<>(number, LocalDateTime.of(2026, 10, 12, 10, number), "user@frc.utn.edu.ar", kind, snapshot);
    }

    private static AllocationHistorySnapshotDto snapshot(Long allocationId, Long classroomId) {
        return new AllocationHistorySnapshotDto(allocationId, 100L, classroomId, AllocationSource.MANUAL, null, null);
    }

    private static ClassroomResponseDto classroom(Long id, Long buildingId) {
        return new ClassroomResponseDto(id, 1, 30, buildingId, "Edificio " + buildingId, 1L, "Aula común", null, true);
    }
}
