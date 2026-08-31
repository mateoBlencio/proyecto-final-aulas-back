package ar.edu.utn.frc.siga.space;

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
import tools.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("PUT /details actualiza tipo, observaciones, recursos y permitido sin tocar número ni capacidad")
    void updateDetails_updatesLocalFieldsOnly() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaNormal();
        Classroom classroom = testData.aula(building, normal, 55);
        testData.tipoRecurso("PC", "Cantidad de PC", ResourceValueKind.COUNT);
        testData.tipoRecurso("PROYECTOR", "Proyector", ResourceValueKind.BOOLEAN);
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();

        String body = """
                {
                  "classroomTypeId": %d,
                  "observations": "Con rampa",
                  "permissionMode": "SUBSET",
                  "permissionTargets": [{"targetKind": "SUBJECT", "targetId": %d}],
                  "resources": [
                    {"code": "PC", "quantity": 20},
                    {"code": "PROYECTOR", "quantity": 1}
                  ]
                }
                """.formatted(normal.getId(), sc.subjectId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value("Con rampa"))
                .andExpect(jsonPath("$.permissionMode").value("SUBSET"))
                .andExpect(jsonPath("$.allowedDisplay").value("Materia-IT"))
                .andExpect(jsonPath("$.resources.length()").value(2))
                .andExpect(jsonPath("$.resources[?(@.code == 'PC')].quantity").value(org.hamcrest.Matchers.hasItem(20)))
                .andExpect(jsonPath("$.roomNumber").value(classroom.getRoomNumber()))
                .andExpect(jsonPath("$.capacity").value(55));

        Classroom reloaded = classroomRepository.findById(classroom.getId()).orElseThrow();
        assertThat(reloaded.getObservations()).isEqualTo("Con rampa");
        assertThat(reloaded.getCapacity()).isEqualTo(55);
    }

    @Test
    @DisplayName("PUT /details con código de recurso desconocido responde 400")
    void updateDetails_unknownResourceCode_returns400() throws Exception {
        Building building = testData.edificio();
        ClassroomType normal = testData.tipoAulaNormal();
        Classroom classroom = testData.aula(building, normal, 40);

        String body = """
                {"classroomTypeId": %d, "permissionMode": "ALL",
                 "resources": [{"code": "PIZARRON_DIGITAL", "quantity": 1}]}
                """.formatted(normal.getId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId() + "/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /v1/classroom-types lista los tipos activos")
    void listClassroomTypes() throws Exception {
        testData.tipoAulaNormal();

        mockMvc.perform(get("/v1/classroom-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description == 'Normal')].enabled").value(org.hamcrest.Matchers.hasItem(true)));
    }
}
