package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Classroom API (integración)")
class ClassroomApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /v1/classrooms crea el aula (201) y persiste en BD")
    void create_persistsClassroomInDatabase() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaNormal();
        String roomNumber = "NEW-" + IntegrationTestData.nextSeq();
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, 1, tipo.getId(), true, building.getId());

        MvcResult result = mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value(roomNumber))
                .andExpect(jsonPath("$.buildingId").value(building.getId()))
                .andReturn();

        Integer id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
        assertThat(classroomRepository.findById(id)).isPresent();
        assertThat(classroomRepository.findById(id).orElseThrow().getRoomNumber()).isEqualTo(roomNumber);
    }

    @Test
    @DisplayName("roomNumber duplicado responde 400 ProblemDetail")
    void create_duplicateRoomNumber_returns400ProblemDetail() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaNormal();
        String roomNumber = "DUP-" + IntegrationTestData.nextSeq();
        ClassroomRequestDto first = new ClassroomRequestDto(roomNumber, 40, 1, tipo.getId(), true, building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        ClassroomRequestDto duplicate = new ClassroomRequestDto(roomNumber, 30, 2, tipo.getId(), true, building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Space domain error"))
                .andExpect(jsonPath("$.detail").value("Classroom roomNumber already exists: " + roomNumber));
    }

    @Test
    @DisplayName("floor > floorCount del edificio responde 400")
    void create_floorExceedsBuildingFloorCount_returns400() throws Exception {
        Building building = testData.edificio("Edificio-Bajo", 2, true);
        ClassroomType tipo = testData.tipoAulaNormal();
        ClassroomRequestDto dto = new ClassroomRequestDto(
                "FLR-" + IntegrationTestData.nextSeq(), 40, 5, tipo.getId(), true, building.getId());

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Space domain error"));
    }

    @Test
    @DisplayName("edificio inactivo responde 404")
    void create_inactiveBuilding_returns404() throws Exception {
        Building building = testData.edificio("Edificio-Inactivo", 3, false);
        ClassroomType tipo = testData.tipoAulaNormal();
        ClassroomRequestDto dto = new ClassroomRequestDto(
                "INA-" + IntegrationTestData.nextSeq(), 40, 1, tipo.getId(), true, building.getId());

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("Bean Validation: campo requerido null responde 400 con detalle de errores por campo")
    void create_missingRequiredField_returns400WithFieldErrors() throws Exception {
        String json = """
                {"capacity":40,"floor":1,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors.roomNumber").exists());
    }

    @Test
    @DisplayName("DELETE hace soft-delete: GET por id responde 404 y no aparece en el listado")
    void delete_softDeletesClassroom_notFoundAfterwards() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaNormal();
        String roomNumber = "DEL-" + IntegrationTestData.nextSeq();
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, 1, tipo.getId(), true, building.getId());

        MvcResult created = mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        int id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(delete("/v1/classrooms/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/classrooms/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isEmpty());

        assertThat(classroomRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("GET listado filtra por buildingId + rango de capacidad y pagina")
    void findAll_filtersByBuildingAndCapacity_andPaginates() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaNormal();
        Classroom chica = testData.aula(building, tipo, 1, 20, true);
        Classroom mediana = testData.aula(building, tipo, 1, 40, true);
        Classroom grande = testData.aula(building, tipo, 1, 80, true);

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("capacityMin", "30")
                        .param("capacityMax", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mediana.getId()));

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }
}
