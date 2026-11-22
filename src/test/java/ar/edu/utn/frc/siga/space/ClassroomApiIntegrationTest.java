package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ClassroomTypeRepository classroomTypeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /v1/classrooms crea el aula (201) y persiste en BD")
    void create_persistsClassroomInDatabase() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, tipo.getId(), building.getId());

        MvcResult result = mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value(roomNumber))
                .andExpect(jsonPath("$.buildingId").value(building.getId()))
                .andReturn();

        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(classroomRepository.findById(id)).isPresent();
        assertThat(classroomRepository.findById(id).orElseThrow().getRoomNumber()).isEqualTo(roomNumber);
    }

    @Test
    @DisplayName("roomNumber duplicado responde 400 ProblemDetail")
    void create_duplicateRoomNumber_returns400ProblemDetail() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        ClassroomRequestDto first = new ClassroomRequestDto(roomNumber, 40, tipo.getId(), building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        ClassroomRequestDto duplicate = new ClassroomRequestDto(roomNumber, 30, tipo.getId(), building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Space domain error"))
                .andExpect(jsonPath("$.detail").value("Ya existe un aula con el número " + roomNumber + " en el edificio."));
    }

    @Test
    @DisplayName("edificio inactivo responde 404")
    void create_inactiveBuilding_returns404() throws Exception {
        Building building = testData.edificio("Edificio-Inactivo", false);
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        ClassroomRequestDto dto = new ClassroomRequestDto(
                (int) IntegrationTestData.nextSeq(), 40, tipo.getId(), building.getId());

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    @DisplayName("Bean Validation: campo requerido null responde 400 con detalle de errores por campo")
    void create_missingRequiredField_returns400WithFieldErrors() throws Exception {
        String json = """
                {"capacity":40,"classroomTypeId":1,"buildingId":1}
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
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, tipo.getId(), building.getId());

        MvcResult created = mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/v1/classrooms/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/classrooms/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isEmpty());

        assertThat(classroomRepository.findActiveById(id)).isEmpty();
        assertThat(classroomRepository.findById(id)).get()
                .satisfies(classroom -> assertThat(classroom.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("PUT /v1/classrooms/{id} de aula sincronizada con otra capacidad responde 409 y no toca la base")
    void update_sysacadClassroomWithDifferentCapacity_returns409AndKeepsRow() throws Exception {
        Classroom classroom = sysacadClassroom(testData.edificio(), testData.tipoAulaPorDefecto(), 40);
        ClassroomRequestDto dto = new ClassroomRequestDto(classroom.getRoomNumber(), 55,
                classroom.getClassroomType().getId(), classroom.getBuilding().getId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Aula sincronizada con SysAcad"))
                .andExpect(jsonPath("$.detail").value("El aula " + classroom.getId()
                        + " viene de SysAcad: no se puede modificar la capacidad."));

        Classroom reloaded = classroomRepository.findById(classroom.getId()).orElseThrow();
        assertThat(reloaded.getCapacity()).isEqualTo(40);
        assertThat(reloaded.getRoomNumber()).isEqualTo(classroom.getRoomNumber());
    }

    @Test
    @DisplayName("PUT /v1/classrooms/{id} de aula sincronizada con los mismos valores y otro tipo responde 200 y guarda el tipo")
    void update_sysacadClassroomWithSameFieldsAndOtherType_returns200() throws Exception {
        Classroom classroom = sysacadClassroom(testData.edificio(), testData.tipoAulaPorDefecto(), 40);
        ClassroomType laboratory = classroomTypeRepository.save(ClassroomType.builder()
                .description("Laboratorio-" + IntegrationTestData.nextSeq()).build());
        ClassroomRequestDto dto = new ClassroomRequestDto(classroom.getRoomNumber(), 40,
                laboratory.getId(), classroom.getBuilding().getId());

        mockMvc.perform(put("/v1/classrooms/" + classroom.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomTypeId").value(laboratory.getId()))
                .andExpect(jsonPath("$.capacity").value(40));

        Classroom reloaded = classroomRepository.findById(classroom.getId()).orElseThrow();
        assertThat(reloaded.getClassroomType().getId()).isEqualTo(laboratory.getId());
        assertThat(reloaded.getCapacity()).isEqualTo(40);
    }

    private Classroom sysacadClassroom(Building building, ClassroomType tipo, int capacity) {
        return classroomRepository.save(Classroom.builder()
                .roomNumber((int) IntegrationTestData.nextSeq())
                .capacity(capacity)
                .building(building)
                .classroomType(tipo)
                .sysacadEnabled(true)
                .sysacadHash("hash-" + IntegrationTestData.nextSeq())
                .build());
    }

    @Test
    @DisplayName("GET listado filtra por buildingId + rango de capacidad y pagina")
    void findAll_filtersByBuildingAndCapacity_andPaginates() throws Exception {
        Building building = testData.edificio();
        ClassroomType tipo = testData.tipoAulaPorDefecto();
        Classroom chica = testData.aula(building, tipo, 20);
        Classroom mediana = testData.aula(building, tipo, 40);
        Classroom grande = testData.aula(building, tipo, 80);

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
