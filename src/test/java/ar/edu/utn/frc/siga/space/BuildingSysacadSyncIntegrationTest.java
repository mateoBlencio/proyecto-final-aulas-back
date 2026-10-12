package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Sync de edificios contra SysAcad y baja lógica (integración)")
class BuildingSysacadSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private BuildingService buildingService;

    @AfterEach
    void restoreCurrentFlag() {
        List<Building> all = buildingRepository.findAll();
        all.forEach(building -> building.setSysacadEnabled(true));
        buildingRepository.saveAll(all);
    }

    @Test
    @DisplayName("un edificio inhabilitado a mano no se reactiva ni cambia eliminado_en con el sync, presente o ausente")
    void manuallyDeactivatedBuildingKeepsDeletedAtAcrossSyncs() {
        int code = 700_000 + (int) (System.nanoTime() % 100_000);
        Building building = Building.builder().name("Edificio-Sync-" + code).buildingCode(code).build();
        building.deactivate();
        Long id = buildingRepository.save(building).getId();
        var deletedAt = buildingRepository.findById(id).orElseThrow().getDeletedAt();
        assertThat(deletedAt).isNotNull();

        asFixtureUser(() -> buildingService.syncBuildings(List.of(new BuildingSyncCommand(code, "Renombrado-" + code))));
        Building afterPresent = buildingRepository.findById(id).orElseThrow();
        assertThat(afterPresent.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(afterPresent.getSysacadEnabled()).isTrue();
        assertThat(afterPresent.getName()).isEqualTo("Renombrado-" + code);

        asFixtureUser(() -> buildingService.syncBuildings(List.of(new BuildingSyncCommand(code + 1, "Otro-" + code))));
        Building afterAbsent = buildingRepository.findById(id).orElseThrow();
        assertThat(afterAbsent.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(afterAbsent.getSysacadEnabled()).isFalse();

        asFixtureUser(() -> buildingService.syncBuildings(List.of(new BuildingSyncCommand(code, "Renombrado-" + code))));
        Building afterReappear = buildingRepository.findById(id).orElseThrow();
        assertThat(afterReappear.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(afterReappear.getSysacadEnabled()).isTrue();
    }

    @Test
    @DisplayName("un edificio activo ausente upstream queda no vigente pero sigue activo (eliminado_en nulo)")
    void activeBuildingAbsentUpstreamStaysActive() {
        int code = 800_000 + (int) (System.nanoTime() % 100_000);
        Long id = buildingRepository.save(Building.builder().name("Edificio-Ausente-" + code).buildingCode(code).build()).getId();

        asFixtureUser(() -> buildingService.syncBuildings(List.of(new BuildingSyncCommand(code + 1, "Otro-" + code))));

        Building after = buildingRepository.findById(id).orElseThrow();
        assertThat(after.getSysacadEnabled()).isFalse();
        assertThat(after.getDeletedAt()).isNull();
    }
}
