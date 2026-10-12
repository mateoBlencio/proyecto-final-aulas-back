package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard - tendencia e informe (integración)")
class DashboardTrendAndReportApiIntegrationTest extends DashboardIntegrationSupport {

    private static final YearMonth SEPTEMBER = YearMonth.of(2022, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2022, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2022, 11);

    @Autowired
    private DashboardSnapshotService snapshotService;

    @BeforeEach
    @AfterEach
    void cleanSnapshots() {
        jdbc.update("DELETE FROM tablero_snapshot");
    }

    /**
     * Snapshot of {@code month} with building A (one overcrowded hour), building B (an adequate two-hour class) and one
     * class without room. Each test that checks campus totals takes its own month: the database is shared and the
     * events of earlier tests stay in it.
     */
    private Building[] monthWithTwoBuildings(YearMonth month) {
        jdbc.update("DELETE FROM feriado WHERE fecha BETWEEN ? AND ?", month.atDay(1), month.atEndOfMonth());
        LocalDate tuesday = month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        Building a = building();
        Building b = building();
        allocated(room(a, 30), tuesday, bandStart(), 60, 31);
        allocated(room(b, 100), tuesday, bandStart(), 120, 60);
        unallocated(tuesday.plusDays(1), bandStart(), 60, 20);
        snapshotService.generate(month, "SISTEMA", null);
        return new Building[]{a, b};
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
    @DisplayName("a restricted user gets the same report on every read too")
    void restrictedReportIsStable() throws Exception {
        Building[] buildings = monthWithTwoBuildings(SEPTEMBER);
        MockMvc auxA = auxiliarOf(buildings[0]);

        String first = auxA.perform(get("/v1/dashboard/reports/2022-09")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = auxA.perform(get("/v1/dashboard/reports/2022-09")).andReturn().getResponse().getContentAsString();

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

    @Test
    @DisplayName("without snapshots the trend has an empty series, no baseline and still the partial current month")
    void trendWithoutSnapshots() throws Exception {
        JsonNode trend = getJson(mockMvc, "/v1/dashboard/allocation-trend");

        assertThat(trend.get("series")).isEmpty();
        assertThat(trend.get("closedMonths")).isEmpty();
        assertThat(trend.get("baselineMonth").isNull()).isTrue();
        assertThat(trend.get("latestClosedMonth").isNull()).isTrue();
        assertThat(trend.get("currentMonth").get("partial").asBoolean()).isTrue();
        assertThat(trend.get("currentMonth").get("month").asText()).isEqualTo(YearMonth.now().toString());
    }

    @Test
    @DisplayName("the trend orders closed months, takes the first as baseline and the last as latest")
    void trendBaselineAndLatest() throws Exception {
        snapshotService.generate(YearMonth.of(2022, 9), "SISTEMA", null);
        snapshotService.generate(YearMonth.of(2022, 7), "SISTEMA", null);

        JsonNode trend = getJson(mockMvc, "/v1/dashboard/allocation-trend");

        assertThat(trend.get("closedMonths")).extracting(JsonNode::asText).containsExactly("2022-07", "2022-09");
        assertThat(trend.get("baselineMonth").asText()).isEqualTo("2022-07");
        assertThat(trend.get("latestClosedMonth").asText()).isEqualTo("2022-09");
    }

    @Test
    @DisplayName("restricted, the report campus is the sum of the user's buildings, not the stored campus row")
    void restrictedReportCampusIsTheSumOfItsBuildings() throws Exception {
        Building[] buildings = monthWithTwoBuildings(OCTOBER);
        Building a = buildings[0];

        JsonNode restricted = getJson(auxiliarOf(a), "/v1/dashboard/reports/2022-10");
        JsonNode global = getJson(mockMvc, "/v1/dashboard/reports/2022-10");

        assertThat(restricted.get("restrictedScope").asBoolean()).isTrue();
        assertThat(restricted.get("buildings")).hasSize(1);
        JsonNode mine = restricted.get("buildings").get(0);
        assertThat(mine.get("buildingId").asLong()).isEqualTo(a.getId());
        JsonNode campus = restricted.get("campus");
        assertThat(campus.get("roomCount").asInt()).isEqualTo(1);
        assertThat(campus.get("allocationStatus")).isEqualTo(mine.get("allocationStatus"));
        assertThat(campus.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(campus.get("occupancy")).isEqualTo(mine.get("occupancy"));
        assertThat(campus.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
        assertThat(campus.get("hourlyProfile")).isEqualTo(mine.get("hourlyProfile"));
        assertThat(campus.get("roomRequests")).isEqualTo(mine.get("roomRequests"));
        assertThat(campus.get("unallocatedClasses").isNull()).isTrue();
        assertThat(campus.get("unassignedBuildingRoomRequests").isNull()).isTrue();
        assertThat(global.get("campus").get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(global.get("campus").get("occupancy").get("occupiedHours").asDouble()).isEqualTo(3.0);
        assertThat(global.get("campus").get("unallocatedClasses").asLong()).isEqualTo(1);
        assertThat(global.get("campus").get("unassignedBuildingRoomRequests").isNull()).isFalse();
    }

    @Test
    @DisplayName("restricted, the trend series is the sum of the user's buildings and carries no unallocated count")
    void restrictedTrendIsTheSumOfItsBuildings() throws Exception {
        Building[] buildings = monthWithTwoBuildings(NOVEMBER);

        JsonNode restricted = getJson(auxiliarOf(buildings[0]), "/v1/dashboard/allocation-trend");
        JsonNode global = getJson(mockMvc, "/v1/dashboard/allocation-trend");

        assertThat(restricted.get("restrictedScope").asBoolean()).isTrue();
        JsonNode month = restricted.get("series").get(0);
        assertThat(month.get("month").asText()).isEqualTo("2022-11");
        assertThat(month.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(month.get("unallocatedClasses").isNull()).isTrue();
        assertThat(global.get("series").get(0).get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(global.get("series").get(0).get("unallocatedClasses").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("restricted, availableDays of the report campus equals the one of its buildings and of the month")
    void restrictedCampusAvailableDaysMatchItsBuildings() throws Exception {
        Building[] buildings = monthWithTwoBuildings(SEPTEMBER);

        JsonNode restricted = getJson(auxiliarOf(buildings[0]), "/v1/dashboard/reports/2022-09");
        JsonNode global = getJson(mockMvc, "/v1/dashboard/reports/2022-09");

        int monthDays = global.get("campus").get("availableDays").asInt();
        assertThat(monthDays).isEqualTo(26);
        assertThat(restricted.get("buildings").get(0).get("availableDays").asInt()).isEqualTo(monthDays);
        assertThat(restricted.get("campus").get("availableDays").asInt()).isEqualTo(monthDays);
    }

    @Test
    @DisplayName("restricted to a building created after the snapshot, the campus still reports the month's available days")
    void restrictedCampusWithoutBuildingsKeepsAvailableDays() throws Exception {
        monthWithTwoBuildings(SEPTEMBER);
        Building createdLater = building();

        JsonNode restricted = getJson(auxiliarOf(createdLater), "/v1/dashboard/reports/2022-09");
        JsonNode global = getJson(mockMvc, "/v1/dashboard/reports/2022-09");

        assertThat(restricted.get("buildings")).isEmpty();
        assertThat(restricted.get("campus").get("availableDays").asInt())
                .isEqualTo(global.get("campus").get("availableDays").asInt());
    }

    @Test
    @DisplayName("CONSULTA reads trend and report, a user without roles gets 403 on both")
    void permissions() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        consulta().perform(get("/v1/dashboard/allocation-trend")).andExpect(status().isOk());
        consulta().perform(get("/v1/dashboard/reports/2020-03")).andExpect(status().isOk());
        userWithoutRoles().perform(get("/v1/dashboard/allocation-trend")).andExpect(status().isForbidden());
        userWithoutRoles().perform(get("/v1/dashboard/reports/2020-03")).andExpect(status().isForbidden());
    }
}
