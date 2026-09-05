package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("Classroom soft-delete (integración)")
class ClassroomSoftDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomService classroomService;

    @Test
    @DisplayName("un aula borrada desaparece de findByRoomNumberAndDeletedAtIsNull y del listado paginado")
    void softDeletedClassroom_isHiddenFromActiveFindersAndListEndpoint() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long id = classroom.getId();
        Integer roomNumber = classroom.getRoomNumber();

        assertThat(classroomRepository.findByRoomNumberAndDeletedAtIsNull(roomNumber)).isPresent();
        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id.intValue())));

        classroomService.delete(id);

        assertThat(classroomRepository.findByRoomNumberAndDeletedAtIsNull(roomNumber)).isEmpty();
        assertThat(classroomRepository.findAllByRoomNumberAndDeletedAtIsNull(roomNumber)).isEmpty();
        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id.intValue()))));
    }

    @Test
    @DisplayName("un aula borrada se puede re-leer por findById y restaurar, y reaparece (antes imposible con @SQLRestriction)")
    void softDeletedClassroom_canBeReReadByIdAndRestored() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long id = classroom.getId();
        Integer roomNumber = classroom.getRoomNumber();

        classroomService.delete(id);

        Classroom deleted = classroomRepository.findById(id).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        classroomRepository.restore(deleted);

        assertThat(classroomRepository.findByRoomNumberAndDeletedAtIsNull(roomNumber)).isPresent();
        Classroom restored = classroomRepository.findById(id).orElseThrow();
        assertThat(restored.isActive()).isTrue();
        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id.intValue())));
    }
}
