package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Baja de edificio: libera y fusiona las clases futuras de sus aulas (integración)")
class BuildingDeactivationReleaseApiIntegrationTest extends AbstractDeactivationIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    private static String batchBody(Long buildingId, boolean active) {
        return "{\"buildings\":[{\"id\":" + buildingId + ",\"active\":" + active + "}]}";
    }

    @Test
    @DisplayName("principal y espejo en dos aulas del edificio: 1 clase liberada, 0 fusionadas, nota de edificio y espejo MIRROR_RETIRED")
    void bajaDelEdificioLiberaLaClaseCompleta() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        Long principal = allocatedOccurrence(r204, futureMonday());
        Long mirror = mirrorAllocatedIn(principal, r205);

        mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(1))
                .andExpect(jsonPath("$.mergedClasses").value(0))
                .andExpect(jsonPath("$.affectedRoomRequests").value(0));

        assertReleased(principal, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, r204);
        assertReleased(mirror, ReleaseReason.MIRROR_RETIRED, BUILDING_NOTE, r205);
    }

    @Test
    @DisplayName("libera tambien la clase de un aula del edificio que ya estaba dada de baja")
    void liberaEnUnAulaYaDadaDeBaja() throws Exception {
        Building building = testData.edificio();
        Classroom active = testData.aula(building);
        Classroom alreadyDeactivated = testData.aula(building);
        Long inActive = allocatedOccurrence(active, futureMonday());
        Long inDeactivated = createOccurrence(futureMonday(), LocalTime.of(18, 0));
        allocateDirectly(inDeactivated, alreadyDeactivated);
        classroomRepository.softDelete(alreadyDeactivated);

        mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(2));

        assertReleased(inActive, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, active);
        assertReleased(inDeactivated, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, alreadyDeactivated);
    }

    @Test
    @DisplayName("PATCH /v1/buildings/activation con active=false libera y responde la lista de edificios")
    void patchLoteDesactivaYLibera() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long occurrenceId = allocatedOccurrence(classroom, futureMonday());

        mockMvc.perform(patch("/v1/buildings/activation").contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(building.getId(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(building.getId().intValue()))
                .andExpect(jsonPath("$[0].active").value(false));

        assertReleased(occurrenceId, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, classroom);
    }

    @Test
    @DisplayName("PATCH /v1/buildings/activation con active=true no toca asignaciones ni revierte liberaciones")
    void patchLoteActivarNoToca() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long released = allocatedOccurrence(classroom, futureMonday());
        mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId())).andExpect(status().isOk());
        Long keptElsewhere = allocatedOccurrence(testData.aula(testData.edificio()), futureMonday());

        mockMvc.perform(patch("/v1/buildings/activation").contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(building.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));

        assertReleased(released, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, classroom);
        assertThat(allocationOf(keptElsewhere)).isPresent();
    }

    @Test
    @DisplayName("PUT /v1/buildings/{id}/activation reactiva sin revertir la liberacion")
    void reactivarNoRevierteLaLiberacion() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long released = allocatedOccurrence(classroom, futureMonday());
        mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId())).andExpect(status().isOk());

        mockMvc.perform(put("/v1/buildings/{id}/activation", building.getId())).andExpect(status().isNoContent());

        assertReleased(released, ReleaseReason.CLASSROOM_DEACTIVATED, BUILDING_NOTE, classroom);
    }

    @Test
    @DisplayName("GET /v1/buildings/{id}/deactivation-impact informa lo mismo que la baja sin escribir; 404 si no existe")
    void impactoDelEdificio() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long occurrenceId = allocatedOccurrence(classroom, futureMonday());

        String impact = mockMvc.perform(get("/v1/buildings/{id}/deactivation-impact", building.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedClasses").value(1))
                .andReturn().getResponse().getContentAsString();

        assertAllocatedIn(occurrenceId, classroom);
        String applied = mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(applied).isEqualTo(impact);
        mockMvc.perform(get("/v1/buildings/{id}/deactivation-impact", 999_999_999L))
                .andExpect(status().isNotFound());
    }
}
