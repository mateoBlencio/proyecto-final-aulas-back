package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import java.util.List;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Classroom roomNumber unico por edificio (integración)")
class ClassroomRoomNumberPerBuildingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ClassroomService classroomService;

    @Autowired
    private ObjectMapper objectMapper;

    private void postClassroom(int roomNumber, ClassroomType type, Building building, int expectedStatus)
            throws Exception {
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, type.getId(), building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("el mismo roomNumber se crea en dos edificios distintos")
    void sameRoomNumberInDifferentBuildings_isCreated() throws Exception {
        Building first = testData.edificio();
        Building second = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();

        postClassroom(roomNumber, type, first, 201);
        postClassroom(roomNumber, type, second, 201);

        assertThat(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, first)).isPresent();
        assertThat(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, second)).isPresent();
    }

    @Test
    @DisplayName("el mismo roomNumber en el mismo edificio es rechazado por el servicio con 400")
    void sameRoomNumberInSameBuilding_isRejectedByService() throws Exception {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        postClassroom(roomNumber, type, building, 201);

        ClassroomRequestDto duplicate = new ClassroomRequestDto(roomNumber, 30, type.getId(), building.getId());
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Ya existe un aula con el número " + roomNumber + " en el edificio."));
    }

    @Test
    @DisplayName("el indice unico rechaza un insert directo con el mismo roomNumber en el mismo edificio")
    void sameRoomNumberInSameBuilding_isRejectedByUniqueIndex() {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        testData.aulaConNumero(roomNumber, building, type, 40);

        assertThatThrownBy(() -> testData.aulaConNumero(roomNumber, building, type, 30))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("el indice unico permite el mismo roomNumber en edificios distintos")
    void sameRoomNumberInDifferentBuildings_isAllowedByUniqueIndex() {
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();

        Classroom a = testData.aulaConNumero(roomNumber, testData.edificio(), type, 40);
        Classroom b = testData.aulaConNumero(roomNumber, testData.edificio(), type, 40);

        assertThat(a.getId()).isNotEqualTo(b.getId());
    }

    @Test
    @DisplayName("un aula con baja logica no bloquea reusar el roomNumber en el mismo edificio")
    void softDeletedClassroom_doesNotBlockReuseOfRoomNumber() throws Exception {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        Classroom original = testData.aula(building);
        int roomNumber = original.getRoomNumber();

        mockMvc.perform(delete("/v1/classrooms/" + original.getId())).andExpect(status().isNoContent());

        postClassroom(roomNumber, type, building, 201);

        assertThat(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, building))
                .get().extracting(Classroom::getId).isNotEqualTo(original.getId());
    }

    private void putClassroom(Long id, int roomNumber, ClassroomType type, Building building, int expectedStatus)
            throws Exception {
        ClassroomRequestDto dto = new ClassroomRequestDto(roomNumber, 40, type.getId(), building.getId());
        mockMvc.perform(put("/v1/classrooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("PUT con roomNumber usado por otra aula del edificio responde 400 con el mismo mensaje que create")
    void update_withDuplicateRoomNumber_isRejectedWith400() throws Exception {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        Classroom target = testData.aula(building);
        Classroom other = testData.aula(building);
        int taken = other.getRoomNumber();

        ClassroomRequestDto dto = new ClassroomRequestDto(taken, 40, type.getId(), building.getId());
        mockMvc.perform(put("/v1/classrooms/" + target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Ya existe un aula con el número " + taken + " en el edificio."));
        assertThat(classroomRepository.findById(target.getId()).orElseThrow().getRoomNumber())
                .isNotEqualTo(taken);
    }

    @Test
    @DisplayName("PUT que conserva su propio roomNumber y edificio responde 200")
    void update_keepingOwnRoomNumber_isAccepted() throws Exception {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        Classroom target = testData.aula(building);

        putClassroom(target.getId(), target.getRoomNumber(), type, building, 200);
    }

    @Test
    @DisplayName("PUT al mismo roomNumber en otro edificio, sin choque allí, responde 200")
    void update_toBuildingWithoutThatRoomNumber_isAccepted() throws Exception {
        Building first = testData.edificio();
        Building second = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        Classroom inFirst = testData.aula(first);
        testData.aula(second);

        putClassroom(inFirst.getId(), inFirst.getRoomNumber(), type, second, 200);

        assertThat(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(inFirst.getRoomNumber(), second))
                .get().extracting(Classroom::getId).isEqualTo(inFirst.getId());
    }

    @Test
    @DisplayName("PUT que mueve el aula a un edificio donde el roomNumber ya existe responde 400")
    void update_toBuildingWhereRoomNumberIsTaken_isRejectedWith400() throws Exception {
        Building first = testData.edificio();
        Building second = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        Classroom moving = testData.aulaConNumero(roomNumber, first, type, 40);
        testData.aulaConNumero(roomNumber, second, type, 40);

        putClassroom(moving.getId(), roomNumber, type, second, 400);
    }

    @Test
    @DisplayName("activar un aula borrada cuando otra activa usa su roomNumber en el edificio responde 400")
    void activate_withRoomNumberTakenByActiveClassroom_isRejectedWith400() throws Exception {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        Classroom original = testData.aulaConNumero(roomNumber, building, type, 40);
        mockMvc.perform(delete("/v1/classrooms/" + original.getId())).andExpect(status().isNoContent());
        testData.aulaConNumero(roomNumber, building, type, 40);

        mockMvc.perform(put("/v1/classrooms/" + original.getId() + "/activation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Ya existe un aula con el número " + roomNumber + " en el edificio."));
        assertThat(classroomRepository.findById(original.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("activar un aula borrada sin choque de roomNumber responde 204 y la deja activa")
    void activate_withoutConflict_isAccepted() throws Exception {
        Building building = testData.edificio();
        Classroom original = testData.aula(building);
        mockMvc.perform(delete("/v1/classrooms/" + original.getId())).andExpect(status().isNoContent());

        mockMvc.perform(put("/v1/classrooms/" + original.getId() + "/activation"))
                .andExpect(status().isNoContent());

        assertThat(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(original.getRoomNumber(), building))
                .isPresent();
    }

    @Test
    @DisplayName("activar un aula borrada cuyo roomNumber lo usa un aula activa de otro edificio responde 204")
    void activate_withRoomNumberUsedInAnotherBuilding_isAccepted() throws Exception {
        Building first = testData.edificio();
        Building second = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        Classroom original = testData.aulaConNumero(roomNumber, first, type, 40);
        mockMvc.perform(delete("/v1/classrooms/" + original.getId())).andExpect(status().isNoContent());
        testData.aulaConNumero(roomNumber, second, type, 40);

        mockMvc.perform(put("/v1/classrooms/" + original.getId() + "/activation"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("sync actualiza la fila borrada y la activa del mismo (edificio, número) sin excepción ni filas nuevas")
    void sync_withDeletedAndActivePair_updatesBothRows() throws Exception {
        Building building = testData.edificio();
        building.setBuildingCode((int) (100_000 + IntegrationTestData.nextSeq()));
        buildingRepository.save(building);
        ClassroomType type = testData.tipoAulaPorDefecto();
        int roomNumber = (int) IntegrationTestData.nextSeq();
        Classroom deleted = testData.aulaConNumero(roomNumber, building, type, 40);
        mockMvc.perform(delete("/v1/classrooms/" + deleted.getId())).andExpect(status().isNoContent());
        Classroom active = testData.aulaConNumero(roomNumber, building, type, 50);
        long rowsBefore = classroomRepository.count();

        int affected = classroomService.syncClassrooms(
                List.of(new ClassroomSyncCommand(roomNumber, building.getBuildingCode(), true, 70)));

        assertThat(affected).isGreaterThanOrEqualTo(2);
        assertThat(classroomRepository.count()).isEqualTo(rowsBefore);
        Classroom reloadedDeleted = classroomRepository.findById(deleted.getId()).orElseThrow();
        Classroom reloadedActive = classroomRepository.findById(active.getId()).orElseThrow();
        assertThat(reloadedDeleted.getCapacity()).isEqualTo(70);
        assertThat(reloadedActive.getCapacity()).isEqualTo(70);
        assertThat(reloadedDeleted.isDeleted()).isTrue();
        assertThat(reloadedActive.isActive()).isTrue();
    }
}
