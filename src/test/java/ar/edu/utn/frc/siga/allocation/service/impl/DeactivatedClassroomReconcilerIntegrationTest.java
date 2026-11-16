package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeactivatedClassroomReconciler (integración)")
class DeactivatedClassroomReconcilerIntegrationTest extends AbstractDeactivationIntegrationTest {

    @Autowired
    private DeactivatedClassroomReconciler reconciler;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private BuildingRepository buildingRepository;

    private void reconcile() {
        RequestContextHolder.resetRequestAttributes();
        reconciler.run(new DefaultApplicationArguments());
    }

    private long lastRevision() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Long.class);
    }

    private long publications() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication", Long.class);
    }

    private Map<Long, Long> allocationSnapshot() {
        return jdbc.query("SELECT id_ocurrencia, id_aula FROM asignacion_aula",
                rs -> {
                    Map<Long, Long> snapshot = new HashMap<>();
                    while (rs.next()) {
                        snapshot.put(rs.getLong(1), rs.getLong(2));
                    }
                    return snapshot;
                });
    }

    @Test
    @DisplayName("libera la clase de un aula dada de baja, fusiona la de un edificio dado de baja con espejo en aula activa y es idempotente")
    void liberaFusionaYEsIdempotente() throws Exception {
        Classroom deactivated = testData.aula(testData.edificio());
        Long inDeactivated = allocatedOccurrence(deactivated, futureMonday());

        Building deactivatedBuilding = testData.edificio();
        Building activeBuilding = testData.edificio();
        Classroom inBuilding = testData.aula(deactivatedBuilding);
        Classroom activeMirrorRoom = testData.aula(activeBuilding);
        Long principal = allocatedOccurrence(inBuilding, futureMonday().plusWeeks(1));
        Long mirror = mirrorAllocatedIn(principal, activeMirrorRoom);
        Long principalAllocationId = allocationOf(principal).orElseThrow().getId();

        Classroom disabledInSysacad = classroomRepository.save(Classroom.builder()
                .roomNumber((int) IntegrationTestData.nextSeq()).capacity(40).building(activeBuilding)
                .classroomType(testData.tipoAulaPorDefecto()).sysacadEnabled(false).sysacadHash("hash").build());
        Long inDisabled = createOccurrence(futureMonday().plusWeeks(2), LocalTime.of(18, 0));
        allocateDirectly(inDisabled, disabledInSysacad);

        classroomRepository.softDelete(deactivated);
        buildingRepository.softDelete(deactivatedBuilding);
        long revisionBefore = lastRevision();
        long publicationsBefore = publications();

        reconcile();

        assertReleased(inDeactivated, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, deactivated);
        assertAllocatedIn(principal, activeMirrorRoom);
        assertThat(allocationOf(principal).orElseThrow().getId()).isEqualTo(principalAllocationId);
        assertReleased(mirror, ReleaseReason.MIRROR_RETIRED, BUILDING_NOTE, activeMirrorRoom);
        assertAllocatedIn(inDisabled, disabledInSysacad);
        assertThat(occurrence(inDisabled).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);

        assertThat(jdbc.queryForList("SELECT tipo_actor FROM revinfo WHERE rev > ?", String.class, revisionBefore))
                .containsExactly("SYSTEM");
        assertThat(publications()).isEqualTo(publicationsBefore);

        long revisionAfterFirstRun = lastRevision();
        Map<Long, Long> allocationsAfterFirstRun = allocationSnapshot();

        reconcile();

        assertThat(lastRevision()).isEqualTo(revisionAfterFirstRun);
        assertThat(allocationSnapshot()).isEqualTo(allocationsAfterFirstRun);
        assertThat(publications()).isEqualTo(publicationsBefore);
        assertThat(List.of(inDeactivated, mirror)).allSatisfy(
                id -> assertThat(occurrence(id).getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED));
    }
}
