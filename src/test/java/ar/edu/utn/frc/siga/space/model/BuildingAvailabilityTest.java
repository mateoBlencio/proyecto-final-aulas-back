package ar.edu.utn.frc.siga.space.model;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Building.isAvailable y unavailableReason")
class BuildingAvailabilityTest {

    private static final String HASH = "hash-sysacad";

    private static Building building(boolean active, boolean enabled, String hash) {
        Building building = Building.builder().id(1L).sysacadEnabled(enabled).sysacadHash(hash).build();
        if (!active) {
            building.deactivate();
        }
        return building;
    }

    @Test
    @DisplayName("un edificio activo y vigente está disponible")
    void activeCurrentBuildingIsAvailable() {
        Building building = building(true, true, HASH);

        assertThat(building.unavailableReason()).isNull();
        assertThat(building.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("un edificio nuevo sin datos de sync es vigente por defecto")
    void newBuildingIsCurrentByDefault() {
        Building building = Building.builder().build();

        assertThat(building.getSysacadEnabled()).isTrue();
        assertThat(building.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("un edificio dado de baja es DEACTIVATED")
    void deactivatedBuildingIsDeactivated() {
        Building building = building(false, true, HASH);

        assertThat(building.unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
        assertThat(building.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("un edificio con hash y flag apagado es NOT_IN_SYSACAD")
    void absentBuildingIsNotInSysacad() {
        Building building = building(true, false, HASH);

        assertThat(building.unavailableReason()).isEqualTo(UnavailableReason.NOT_IN_SYSACAD);
        assertThat(building.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("un edificio manual (hash nulo, flag apagado) sigue disponible")
    void manualBuildingStaysAvailable() {
        Building building = building(true, false, null);

        assertThat(building.unavailableReason()).isNull();
        assertThat(building.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("la baja del edificio gana sobre no estar vigente")
    void deactivationWinsOverNotInSysacad() {
        assertThat(building(false, false, HASH).unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

}
