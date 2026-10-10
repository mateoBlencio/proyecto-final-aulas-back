package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRepository;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard snapshots (integración)")
class DashboardSnapshotIntegrationTest extends AbstractIntegrationTest {

    private static final YearMonth MONTH = YearMonth.of(2020, 3);

    @Autowired
    private DashboardSnapshotService snapshotService;
    @Autowired
    private DashboardSnapshotRepository snapshotRepository;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tablero_snapshot WHERE anio = ?", MONTH.getYear());
    }

    @Test
    @DisplayName("generate stores one row per active building plus a campus row with versions in the JSON")
    void generateStoresBuildingRowsAndCampusRow() throws Exception {
        testData.edificio();
        Integer activeBuildings = jdbc.queryForObject("SELECT COUNT(*) FROM edificio WHERE eliminado_en IS NULL", Integer.class);

        snapshotService.generate(MONTH, "SISTEMA", null);

        List<DashboardSnapshot> rows = snapshotRepository.findByYearAndMonthAndCurrentTrue(2020, 3);
        assertThat(rows).hasSize(activeBuildings + 1);
        DashboardSnapshot campus = rows.stream().filter(r -> r.getBuildingId() == null).findFirst().orElseThrow();
        assertThat(rows.stream().filter(r -> r.getBuildingId() == null)).hasSize(1);
        assertThat(campus.getGeneratedBy()).isEqualTo("SISTEMA");
        JsonNode json = objectMapper.readTree(campus.getPayload());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("rulesVersion").asInt()).isEqualTo(1);
        assertThat(json.get("month").asText()).isEqualTo("2020-03");
        LocalTime hoursStart = LocalTime.parse(json.get("parameters").get("hoursStart").asText());
        LocalTime hoursEnd = LocalTime.parse(json.get("parameters").get("hoursEnd").asText());
        assertThat(json.get("hourlyProfile")).hasSize(hoursEnd.minusMinutes(1).getHour() - hoursStart.getHour() + 1);
        assertThat(json.get("bySource")).hasSize(4);
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_typeof(datos) FROM tablero_snapshot WHERE id_snapshot = ?", String.class, campus.getId()))
                .isEqualTo("object");
    }
}
