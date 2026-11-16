package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Baja de aula: libera y fusiona las clases futuras (integración)")
class ClassroomDeactivationReleaseApiIntegrationTest extends AbstractDeactivationIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    @Test
    @DisplayName("la clase futura se libera con CLASSROOM_DEACTIVATED; la pasada y la de hoy ya empezada conservan su aula")
    void liberaSoloLaFutura() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long future = allocatedOccurrence(classroom, futureMonday());
        Long past = createOccurrence(LocalDate.now().minusDays(3), LocalTime.of(18, 0));
        allocateDirectly(past, classroom);
        Long startedToday = createOccurrence(LocalDate.now(), LocalTime.MIN);
        allocateDirectly(startedToday, classroom);

        deactivateClassroom(classroom)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(1))
                .andExpect(jsonPath("$.mergedClasses").value(0))
                .andExpect(jsonPath("$.affectedRoomRequests").value(0))
                .andExpect(jsonPath("$.visibleRoomRequests").isEmpty());

        assertReleased(future, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, classroom);
        assertAllocatedIn(past, classroom);
        assertAllocatedIn(startedToday, classroom);
    }

    @Test
    @DisplayName("repetir la baja responde 200 con 0 y 0 y no toca la clase ya liberada")
    void repetirLaBajaEsIdempotente() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long future = allocatedOccurrence(classroom, futureMonday());
        deactivateClassroom(classroom).andExpect(status().isOk());

        deactivateClassroom(classroom)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(0))
                .andExpect(jsonPath("$.mergedClasses").value(0));

        assertReleased(future, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, classroom);
    }

    @Test
    @DisplayName("baja del aula de la principal (204) con espejo en la 205: la principal queda en la 205 con la misma asignacion y el espejo se retira")
    void bajaDelAulaDeLaPrincipalMueveLaPrincipalAlEspejo() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        Long principal = allocatedOccurrence(r204, futureMonday());
        Long mirror = mirrorAllocatedIn(principal, r205);
        Long principalAllocationId = allocationOf(principal).orElseThrow().getId();

        deactivateClassroom(r204)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(0))
                .andExpect(jsonPath("$.mergedClasses").value(1));

        assertAllocatedIn(principal, r205);
        assertThat(allocationOf(principal).orElseThrow().getId()).isEqualTo(principalAllocationId);
        assertReleased(mirror, ReleaseReason.MIRROR_RETIRED, CLASSROOM_NOTE, r205);
    }

    @Test
    @DisplayName("baja del aula del espejo (205): la principal sigue en la 204 y el espejo queda MIRROR_RETIRED")
    void bajaDelAulaDelEspejoLoRetira() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        Long principal = allocatedOccurrence(r204, futureMonday());
        Long mirror = mirrorAllocatedIn(principal, r205);

        deactivateClassroom(r205)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(0))
                .andExpect(jsonPath("$.mergedClasses").value(1));

        assertAllocatedIn(principal, r204);
        assertReleased(mirror, ReleaseReason.MIRROR_RETIRED, CLASSROOM_NOTE, r205);
    }

    @Test
    @DisplayName("un aula deshabilitada en SysAcad y sin baja de SIGA no se toca al dar de baja otra aula")
    void aulaDeshabilitadaEnSysacadNoSeToca() throws Exception {
        Building building = testData.edificio();
        Classroom target = testData.aula(building);
        Classroom disabledInSysacad = classroomRepository.save(Classroom.builder()
                .roomNumber((int) IntegrationTestData.nextSeq()).capacity(40).building(building)
                .classroomType(testData.tipoAulaPorDefecto()).sysacadEnabled(false).sysacadHash("hash").build());
        Long inTarget = allocatedOccurrence(target, futureMonday());
        Long inDisabled = createOccurrence(futureMonday(), LocalTime.of(18, 0));
        allocateDirectly(inDisabled, disabledInSysacad);

        deactivateClassroom(target)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(1));

        assertReleased(inTarget, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, target);
        assertAllocatedIn(inDisabled, disabledInSysacad);
    }

    @Test
    @DisplayName("DELETE /v1/classrooms/{id} tambien libera la clase futura y responde 204")
    void eliminarAulaTambienLibera() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long future = allocatedOccurrence(classroom, futureMonday());

        mockMvc.perform(delete("/v1/classrooms/{id}", classroom.getId())).andExpect(status().isNoContent());

        assertReleased(future, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, classroom);
    }

    @Test
    @DisplayName("GET deactivation-impact devuelve lo mismo que la baja y no cambia nada en la base")
    void impactoCoincideConLaBajaYNoEscribe() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        Long released = allocatedOccurrence(r204, futureMonday());
        Long principal = allocatedOccurrence(r205, futureMonday().plusWeeks(1));
        Long mirror = mirrorAllocatedIn(principal, r204);
        long revisionsBefore = jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Long.class);

        String impact = classroomImpact(r204).andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(1))
                .andExpect(jsonPath("$.mergedClasses").value(1))
                .andReturn().getResponse().getContentAsString();

        assertAllocatedIn(released, r204);
        assertAllocatedIn(mirror, r204);
        assertThat(occurrence(mirror).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Long.class))
                .isEqualTo(revisionsBefore);

        String applied = deactivateClassroom(r204).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(applied).isEqualTo(impact);
    }

    @Test
    @DisplayName("GET deactivation-impact responde 404 a un auxiliar sin alcance sobre el edificio del aula y 200 al de su edificio")
    void impactoSegunElAlcance() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Building otherBuilding = testData.edificio();
        MockMvc auxiliarOfOtherBuilding = mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, otherBuilding.getId());
        MockMvc auxiliarOfBuilding = mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());

        auxiliarOfOtherBuilding.perform(get("/v1/classrooms/{id}/deactivation-impact", classroom.getId()))
                .andExpect(status().isNotFound());
        auxiliarOfBuilding.perform(get("/v1/classrooms/{id}/deactivation-impact", classroom.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET deactivation-impact de un aula inexistente responde 404")
    void impactoDeAulaInexistente() throws Exception {
        mockMvc.perform(get("/v1/classrooms/{id}/deactivation-impact", 999_999_999L))
                .andExpect(status().isNotFound());
    }
}
