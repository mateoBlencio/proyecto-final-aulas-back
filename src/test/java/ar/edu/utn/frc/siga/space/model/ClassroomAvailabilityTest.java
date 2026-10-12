package ar.edu.utn.frc.siga.space.model;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Classroom.isAvailable: la baja del edificio oculta al aula sin modificarla")
class ClassroomAvailabilityTest {

    private static final String HASH = "hash-sysacad";

    private static Building building(boolean active, boolean enabled, String hash) {
        Building building = Building.builder().id(1L).sysacadEnabled(enabled).sysacadHash(hash).build();
        if (!active) {
            building.deactivate();
        }
        return building;
    }

    private static Classroom classroom(Building building, boolean active, boolean enabled, String hash) {
        Classroom classroom = Classroom.builder().id(1L).building(building)
                .sysacadEnabled(enabled).sysacadHash(hash).build();
        if (!active) {
            classroom.deactivate();
        }
        return classroom;
    }

    private static Classroom classroom(boolean buildingActive, boolean classroomActive) {
        return classroom(building(buildingActive, true, HASH), classroomActive, true, HASH);
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

    @Test
    @DisplayName("un aula activa, vigente y de edificio activo y vigente no tiene motivo de no disponibilidad")
    void availableClassroomHasNoReason() {
        assertThat(classroom(true, true).unavailableReason()).isNull();
    }

    @Test
    @DisplayName("un aula dada de baja es DEACTIVATED")
    void deactivatedClassroomIsDeactivated() {
        assertThat(classroom(true, false).unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("un aula activa de un edificio dado de baja es PARENT_DEACTIVATED")
    void classroomOfDeactivatedBuildingIsParentDeactivated() {
        assertThat(classroom(false, true).unavailableReason()).isEqualTo(UnavailableReason.PARENT_DEACTIVATED);
    }

    @Test
    @DisplayName("baja propia y baja del edificio a la vez: gana DEACTIVATED")
    void ownDeactivationWinsOverParentDeactivation() {
        assertThat(classroom(false, false).unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("un aula con hash y flag apagado sigue disponible y no vigente")
    void classroomAbsentFromSysacadStaysAvailable() {
        Classroom classroom = classroom(building(true, true, HASH), true, false, HASH);

        assertThat(classroom.unavailableReason()).isNull();
        assertThat(classroom.isAvailable()).isTrue();
        assertThat(classroom.isCurrentInSysacad()).isFalse();
    }

    @Test
    @DisplayName("un aula no vigente en un edificio no vigente es NOT_IN_SYSACAD")
    void notCurrentClassroomInNotCurrentBuildingIsNotInSysacad() {
        Classroom classroom = classroom(building(true, false, HASH), true, false, HASH);

        assertThat(classroom.unavailableReason()).isEqualTo(UnavailableReason.NOT_IN_SYSACAD);
        assertThat(classroom.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("un aula vigente en un edificio no vigente es NOT_IN_SYSACAD")
    void currentClassroomInNotCurrentBuildingIsNotInSysacad() {
        Classroom classroom = classroom(building(true, false, HASH), true, true, HASH);

        assertThat(classroom.unavailableReason()).isEqualTo(UnavailableReason.NOT_IN_SYSACAD);
        assertThat(classroom.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("un aula cargada a mano (hash nulo, flag apagado) sigue disponible")
    void manualClassroomStaysAvailable() {
        Classroom classroom = classroom(building(true, true, HASH), true, false, null);

        assertThat(classroom.unavailableReason()).isNull();
        assertThat(classroom.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("un aula manual en un edificio manual sigue disponible aunque ambos tengan el flag apagado")
    void manualClassroomInManualBuildingStaysAvailable() {
        Classroom classroom = classroom(building(true, false, null), true, false, null);

        assertThat(classroom.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("la baja del edificio gana sobre un edificio no vigente")
    void parentDeactivationWinsOverNotInSysacad() {
        Classroom classroom = classroom(building(false, false, HASH), true, false, HASH);

        assertThat(classroom.unavailableReason()).isEqualTo(UnavailableReason.PARENT_DEACTIVATED);
    }

    @Test
    @DisplayName("la baja propia gana sobre no estar vigente")
    void ownDeactivationWinsOverNotInSysacad() {
        Classroom classroom = classroom(building(true, false, HASH), false, false, HASH);

        assertThat(classroom.unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

}
