package ar.edu.utn.frc.siga.space;

import static ar.edu.utn.frc.siga.space.service.ClassroomService.DEFAULT_CLASSROOM_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

@Import(IntegrationTestData.class)
@DisplayName("Classroom edición de campos locales (integración)")
class ClassroomDetailsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Test
    @DisplayName("PUT /details actualiza tipo, observaciones, recursos y permitido sin tocar número ni capacidad")
    void updateDetails_updatesLocalFieldsOnly() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, normal, 55);
        long pcId = testData.tipoRecurso("Cantidad de PC", ResourceValueKind.COUNT).getId();
        long projectorId = testData.tipoRecurso("Proyector", ResourceValueKind.BOOLEAN).getId();
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();

        String body = """
                {
                  "classroomTypeId": %d,
                  "observations": "Con rampa",
                  "permissionMode": "SUBSET",
                  "permissionTargets": [{"targetKind": "SUBJECT", "targetId": %d}],
                  "resources": [
                    {"resourceTypeId": %d, "quantity": 20},
                    {"resourceTypeId": %d, "quantity": 1}
                  ]
                }
                """.formatted(normal.getId(), sc.subjectId(), pcId, projectorId);

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value("Con rampa"))
                .andExpect(jsonPath("$.permissionMode").value("SUBSET"))
                .andExpect(jsonPath("$.allowedDisplay").value("Materia-IT"))
                .andExpect(jsonPath("$.resources.length()").value(2))
                .andExpect(jsonPath("$.resources[?(@.name == 'Cantidad de PC')].quantity").value(org.hamcrest.Matchers.hasItem(20)))
                .andExpect(jsonPath("$.roomNumber").value(classroom.getRoomNumber()))
                .andExpect(jsonPath("$.capacity").value(55));

        Classroom reloaded = classroomRepository.findById(classroom.getId()).orElseThrow();
        assertThat(reloaded.getObservations()).isEqualTo("Con rampa");
        assertThat(reloaded.getCapacity()).isEqualTo(55);
    }

    @Test
    @DisplayName("PUT /details con SUBSET sin targets se normaliza a NONE")
    void updateDetails_subsetWithoutTargets_normalizesToNone() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, normal, 30);

        String body = """
                {"classroomTypeId": %d, "permissionMode": "SUBSET", "permissionTargets": []}
                """.formatted(normal.getId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionMode").value("NONE"))
                .andExpect(jsonPath("$.allowedDisplay").value("Ninguna"))
                .andExpect(jsonPath("$.permissionTargets.length()").value(0));

        Classroom reloaded = classroomRepository.findById(classroom.getId()).orElseThrow();
        assertThat(reloaded.getPermissionMode()).isEqualTo(ar.edu.utn.frc.siga.space.model.PermissionMode.NONE);
    }

    @Test
    @DisplayName("PUT /details con id de tipo de recurso desconocido responde 400")
    void updateDetails_unknownResourceTypeId_returns400() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, normal, 40);

        String body = """
                {"classroomTypeId": %d, "permissionMode": "ALL",
                 "resources": [{"resourceTypeId": 999999999, "quantity": 1}]}
                """.formatted(normal.getId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("PUT /details con recurso BOOLEAN y quantity > 1 responde 400")
    void updateDetails_booleanResourceQuantityAboveOne_returns400() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, normal, 40);
        long projectorId = testData.tipoRecurso("Proyector", ResourceValueKind.BOOLEAN).getId();

        String body = """
                {"classroomTypeId": %d, "permissionMode": "ALL",
                 "resources": [{"resourceTypeId": %d, "quantity": 3}]}
                """.formatted(normal.getId(), projectorId);

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /v1/classroom-types lista paginada de tipos activos")
    void listClassroomTypes() throws Exception {
        testData.tipoAulaPorDefecto();

        mockMvc.perform(get("/v1/classroom-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.description == '" + DEFAULT_CLASSROOM_TYPE + "')].enabled")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }
}
