package ar.edu.utn.frc.siga.space.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Classroom.isAvailable: la baja del edificio oculta al aula sin modificarla")
class ClassroomAvailabilityTest {

    private static Classroom classroom(boolean buildingActive, boolean classroomActive) {
        Building building = Building.builder().id(1L).build();
        if (!buildingActive) {
            building.deactivate();
        }
        Classroom classroom = Classroom.builder().id(1L).building(building).build();
        if (!classroomActive) {
            classroom.deactivate();
        }
        return classroom;
    }

    @Test
    void availableOnlyWhenClassroomAndBuildingAreActive() {
        assertThat(classroom(true, true).isAvailable()).isTrue();
        assertThat(classroom(false, true).isAvailable()).isFalse();
        assertThat(classroom(true, false).isAvailable()).isFalse();
        assertThat(classroom(false, false).isAvailable()).isFalse();
    }

    @Test
    void buildingDeactivationDoesNotChangeClassroomOwnState() {
        assertThat(classroom(false, true).isActive()).isTrue();
    }
}
