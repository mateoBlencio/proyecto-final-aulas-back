package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Dashboard - tendencia e informe (integración)")
class DashboardTrendAndReportApiIntegrationTest extends AbstractIntegrationTest {

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
    @DisplayName("the report of a closed month is the same on every read and the month serializes as yyyy-MM")
    void reportIsStable() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        String first = mockMvc.perform(get("/v1/dashboard/reports/2020-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2020-03"))
                .andExpect(jsonPath("$.generatedBy").value("SISTEMA"))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/v1/dashboard/reports/2020-03"))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("a month without snapshot returns 404 and a malformed month returns 400")
    void missingAndMalformedMonth() throws Exception {
        mockMvc.perform(get("/v1/dashboard/reports/2020-04")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/dashboard/reports/2020-13")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the trend lists the closed month and always includes the partial current month")
    void trendListsClosedMonthAndPartialCurrent() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        mockMvc.perform(get("/v1/dashboard/allocation-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedMonths[0]").value("2020-03"))
                .andExpect(jsonPath("$.baselineMonth").value("2020-03"))
                .andExpect(jsonPath("$.series[0].partial").value(false))
                .andExpect(jsonPath("$.currentMonth.partial").value(true));
    }
}
