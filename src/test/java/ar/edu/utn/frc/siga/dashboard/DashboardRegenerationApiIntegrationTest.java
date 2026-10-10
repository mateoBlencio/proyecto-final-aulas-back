package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Dashboard - POST /v1/dashboard/reports/{month}/regeneration (integración)")
class DashboardRegenerationApiIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"reason\":\"Se corrigió una capacidad\"}";

    @Autowired
    private DashboardSnapshotService snapshotService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanSnapshots() {
        jdbc.update("DELETE FROM tablero_snapshot WHERE anio = 2020");
    }

    @Test
    @DisplayName("regenerating keeps the previous rows as not current and stores the new ones with reason and author")
    void regenerationRetiresPreviousRows() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        Integer rowsPerGeneration = jdbc.queryForObject("SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020", Integer.class);

        mockMvc.perform(post("/v1/dashboard/reports/2020-03/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regenerationReason").value("Se corrigió una capacidad"))
                .andExpect(jsonPath("$.generatedBy").value("integration-test@frc.utn.edu.ar"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020 AND NOT vigente", Integer.class))
                .isEqualTo(rowsPerGeneration);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020 AND vigente", Integer.class))
                .isEqualTo(rowsPerGeneration);
        mockMvc.perform(get("/v1/audit").param("q", "Regeneración del informe 2020-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(
                        "Regeneración del informe 2020-03 del tablero: Se corrigió una capacidad"));
    }

    @Test
    @DisplayName("blank reason is 400, current month is 422, month without snapshot is 404, auxiliar is 403")
    void validationsAndPermissions() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        mockMvc.perform(post("/v1/dashboard/reports/2020-03/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/dashboard/reports/" + YearMonth.now() + "/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/v1/dashboard/reports/2020-04/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mockMvcAs("aux-dashboard@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(post("/v1/dashboard/reports/2020-03/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
}
