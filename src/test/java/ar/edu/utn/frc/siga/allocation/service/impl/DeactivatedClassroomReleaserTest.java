package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeactivatedClassroomReleaser.plan")
class DeactivatedClassroomReleaserTest {

    private static final long R204 = 204L;
    private static final long R205 = 205L;
    private static final long R206 = 206L;
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

    private DeactivatedClassroomReleaser.Plan plan(Long... deactivatedClassrooms) {
        world.stub(allocationRepository, occurrenceService, classroomService);
        Map<Long, String> notes = new LinkedHashMap<>();
        for (Long id : deactivatedClassrooms) {
            notes.put(id, NOTE);
        }
        return releaser.plan(notes);
    }

    @Test
    @DisplayName("sin espejo y con la principal en el aula dada de baja: se libera, sin fusion")
    void principalSinEspejoSeLibera() {
        OccurrenceSlotDto principal = world.principal(1L);
        world.allocate(principal, R204);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.releasedPrincipals()).containsExactly(principal);
        assertThat(plan.retiredMirrors()).isEmpty();
        assertThat(plan.movedPrincipals()).isEmpty();
        assertThat(plan.releasedClasses()).isEqualTo(1);
        assertThat(plan.mergedClasses()).isZero();
        assertThat(plan.noteByOccurrence()).containsEntry(1L, NOTE);
    }

    @Test
    @DisplayName("principal en 204 (baja) y espejo en 205: la principal pasa a la 205, el espejo se retira y la clase se fusiona")
    void principalDadaDeBajaSeMueveAlEspejo() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror = world.mirror(2L, principal, 2);
        world.allocate(principal, R204);
        world.allocate(mirror, R205);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.movedPrincipals()).containsExactly(Map.entry(principal, R205));
        assertThat(plan.retiredMirrors()).containsExactly(mirror);
        assertThat(plan.releasedPrincipals()).isEmpty();
        assertThat(plan.releasedClasses()).isZero();
        assertThat(plan.mergedClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("principal en 204 y espejo en 205 (baja): se retira el espejo, la principal no se mueve y la clase se fusiona")
    void espejoDadoDeBajaSeRetira() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror = world.mirror(2L, principal, 2);
        world.allocate(principal, R204);
        world.allocate(mirror, R205);

        DeactivatedClassroomReleaser.Plan plan = plan(R205);

        assertThat(plan.retiredMirrors()).containsExactly(mirror);
        assertThat(plan.movedPrincipals()).isEmpty();
        assertThat(plan.releasedPrincipals()).isEmpty();
        assertThat(plan.releasedClasses()).isZero();
        assertThat(plan.mergedClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("con dos espejos, la principal toma el aula de mayor capacidad y solo se retira ese espejo")
    void principalTomaElEspejoDeMayorCapacidad() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror205 = world.mirror(2L, principal, 2);
        OccurrenceSlotDto mirror206 = world.mirror(3L, principal, 3);
        world.allocate(principal, R204);
        world.allocate(mirror205, R205);
        world.allocate(mirror206, R206);
        world.capacity(R205, 40);
        world.capacity(R206, 80);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.movedPrincipals()).containsExactly(Map.entry(principal, R206));
        assertThat(plan.retiredMirrors()).containsExactly(mirror206);
        assertThat(plan.mergedClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("con capacidades iguales, la principal toma el espejo de menor roomSlot")
    void empateDeCapacidadDesempataPorRoomSlot() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror206 = world.mirror(3L, principal, 3);
        OccurrenceSlotDto mirror205 = world.mirror(2L, principal, 2);
        world.allocate(principal, R204);
        world.allocate(mirror206, R206);
        world.allocate(mirror205, R205);
        world.capacity(R205, 60);
        world.capacity(R206, 60);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.movedPrincipals()).containsExactly(Map.entry(principal, R205));
        assertThat(plan.retiredMirrors()).containsExactly(mirror205);
    }

    @Test
    @DisplayName("principal y espejo en aulas dadas de baja: la principal se libera, el espejo se retira y no hay fusion")
    void todasLasAulasDeLaClaseEnBaja() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror = world.mirror(2L, principal, 2);
        world.allocate(principal, R204);
        world.allocate(mirror, R205);

        DeactivatedClassroomReleaser.Plan plan = plan(R204, R205);

        assertThat(plan.releasedPrincipals()).containsExactly(principal);
        assertThat(plan.retiredMirrors()).containsExactly(mirror);
        assertThat(plan.movedPrincipals()).isEmpty();
        assertThat(plan.releasedClasses()).isEqualTo(1);
        assertThat(plan.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("principal sin aula y unico espejo en baja: se retira el espejo y no cuenta como fusion")
    void principalSinAulaConEspejoEnBajaNoEsFusion() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror = world.mirror(2L, principal, 2);
        world.allocate(mirror, R205);

        DeactivatedClassroomReleaser.Plan plan = plan(R205);

        assertThat(plan.retiredMirrors()).containsExactly(mirror);
        assertThat(plan.releasedPrincipals()).isEmpty();
        assertThat(plan.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("principal sin aula con un espejo en baja y otro activo: la clase se fusiona")
    void principalSinAulaConOtroEspejoActivoEsFusion() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto mirror205 = world.mirror(2L, principal, 2);
        OccurrenceSlotDto mirror206 = world.mirror(3L, principal, 3);
        world.allocate(mirror205, R205);
        world.allocate(mirror206, R206);

        DeactivatedClassroomReleaser.Plan plan = plan(R205);

        assertThat(plan.retiredMirrors()).containsExactly(mirror205);
        assertThat(plan.mergedClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("una clase de hoy que ya empezo no entra al plan")
    void claseDeHoyYaEmpezadaNoEntra() {
        OccurrenceSlotDto started = world.principal(1L, LocalDate.now(), LocalTime.MIN);
        world.allocate(started, R204);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.releasedClasses()).isZero();
        assertThat(plan.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("una clase de hoy mas tarde si entra al plan")
    void claseDeHoyMasTardeEntra() {
        OccurrenceSlotDto later = world.principal(1L, LocalDate.now(), LocalTime.of(23, 59));
        world.allocate(later, R204);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.releasedPrincipals()).containsExactly(later);
    }

    @Test
    @DisplayName("un espejo ya retirado (MIRROR_RETIRED) no cuenta como aula que queda")
    void espejoRetiradoNoCuentaComoAulaRestante() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto retired = world.retiredMirror(2L, principal, 2);
        world.allocate(principal, R204);
        world.allocate(retired, R205);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.releasedPrincipals()).containsExactly(principal);
        assertThat(plan.retiredMirrors()).isEmpty();
        assertThat(plan.movedPrincipals()).isEmpty();
        assertThat(plan.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("un espejo pendiente sin asignacion no cuenta como aula que queda ni se toca")
    void espejoSinAsignacionNoSeToca() {
        OccurrenceSlotDto principal = world.principal(1L);
        OccurrenceSlotDto pending = world.mirror(2L, principal, 2);
        world.allocate(principal, R204);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.releasedPrincipals()).containsExactly(principal);
        assertThat(plan.retiredMirrors()).doesNotContain(pending);
        assertThat(plan.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("una asignacion en un aula que no esta en la baja no genera plan")
    void aulaNoAfectadaNoGeneraPlan() {
        OccurrenceSlotDto principal = world.principal(1L);
        world.allocate(principal, R206);

        DeactivatedClassroomReleaser.Plan plan = plan(R204);

        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.releasedClasses()).isZero();
        assertThat(plan.mergedClasses()).isZero();
    }
}
