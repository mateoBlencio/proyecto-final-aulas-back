package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(IntegrationTestData.class)
@DisplayName("ClassroomService.findByRoomNumberAndBuilding (integración)")
class ClassroomFindByRoomNumberIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomService classroomService;

    @Test
    @DisplayName("aula existente en el edificio: devuelve el DTO")
    void findByRoomNumberAndBuilding_existingClassroom_returnsDto() {
        testData.tipoAulaNormal();
        Building building = testData.edificio();
        var classroom = testData.aula(building);

        ClassroomResponseDto dto = classroomService.findByRoomNumberAndBuilding(
                classroom.getRoomNumber(), building.getId());

        assertThat(dto.id()).isEqualTo(classroom.getId());
    }

    @Test
    @DisplayName("aula inexistente en el edificio: lanza ResourceNotFoundException, no crea nada")
    void findByRoomNumberAndBuilding_missingClassroom_throwsResourceNotFound() {
        testData.tipoAulaNormal();
        Building building = testData.edificio();

        assertThatThrownBy(() -> classroomService.findByRoomNumberAndBuilding(-999, building.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("edificio inexistente: lanza ResourceNotFoundException")
    void findByRoomNumberAndBuilding_missingBuilding_throwsResourceNotFound() {
        assertThatThrownBy(() -> classroomService.findByRoomNumberAndBuilding(101, -1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
