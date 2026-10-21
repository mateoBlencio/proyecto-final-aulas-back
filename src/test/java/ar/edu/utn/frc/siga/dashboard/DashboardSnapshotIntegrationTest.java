package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRepository;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard snapshots (integración)")
class DashboardSnapshotIntegrationTest extends DashboardIntegrationSupport {

    private static final YearMonth MONTH = YearMonth.of(2020, 3);

    @Autowired
    private DashboardSnapshotService snapshotService;
    @Autowired
    private DashboardSnapshotRepository snapshotRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tablero_snapshot");
    }

    private JsonNode payloadOf(YearMonth month, Long buildingId) throws Exception {
        DashboardSnapshot row = snapshotRepository.findByYearAndMonthAndCurrentTrue(month.getYear(), month.getMonthValue())
                .stream().filter(r -> Objects.equals(r.getBuildingId(), buildingId)).findFirst().orElseThrow();
        return objectMapper.readTree(row.getPayload());
    }

    private static OffsetDateTime at(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static DashboardSnapshot row(int year, int month, Long buildingId, boolean current) {
        return DashboardSnapshot.builder().year(year).month(month).buildingId(buildingId).current(current)
                .schemaVersion(1).rulesVersion(1).generatedAt(Instant.parse("2021-06-01T03:00:00Z"))
                .generatedBy("SISTEMA").payload("{}").build();
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
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(DashboardRules.SCHEMA_VERSION);
        assertThat(json.get("rulesVersion").asInt()).isEqualTo(DashboardRules.RULES_VERSION);
        assertThat(json.get("month").asText()).isEqualTo("2020-03");
        LocalTime hoursStart = LocalTime.parse(json.get("parameters").get("hoursStart").asText());
        LocalTime hoursEnd = LocalTime.parse(json.get("parameters").get("hoursEnd").asText());
        assertThat(json.get("hourlyProfile")).hasSize(hoursEnd.minusMinutes(1).getHour() - hoursStart.getHour() + 1);
        assertThat(json.get("bySource")).hasSize(4);
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_typeof(datos) FROM tablero_snapshot WHERE id_snapshot = ?", String.class, campus.getId()))
                .isEqualTo("object");
    }

    @Test
    @DisplayName("an item created on 15 Feb and resolved on 3 Mar counts as pending at the February close, not at the March one")
    void pendingAtCloseIncludesItemsResolvedLater() throws Exception {
        Building a = building();
        Classroom roomA = room(a, 30);
        Long resolvedLater = item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        createdAt(resolvedLater, LocalDateTime.of(2020, 2, 15, 10, 0));
        resolve(resolvedLater, at(2020, 3, 3, 12));
        assignRoom(resolvedLater, roomA, 1);
        Long createdLastMinuteOfFebruary = item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        createdAt(createdLastMinuteOfFebruary, LocalDateTime.of(2020, 2, 29, 23, 59));
        Long createdAtMarchStart = item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        createdAt(createdAtMarchStart, LocalDateTime.of(2020, 3, 1, 0, 0));

        snapshotService.generate(YearMonth.of(2020, 2), "SISTEMA", null);
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        JsonNode february = payloadOf(YearMonth.of(2020, 2), a.getId()).get("roomRequests");
        JsonNode march = payloadOf(YearMonth.of(2020, 3), a.getId()).get("roomRequests");
        assertThat(february.get("pending").asLong()).isEqualTo(2);
        assertThat(february.get("resolved").asLong()).isZero();
        assertThat(march.get("pending").asLong()).isEqualTo(2);
        assertThat(march.get("resolved").asLong()).isEqualTo(1);
        assertThat(payloadOf(YearMonth.of(2020, 2), null).get("roomRequests").get("pending").asLong())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("two current campus rows for the same month violate uq_tablero_snapshot_vigente, retired ones do not")
    void secondCurrentCampusRowViolatesUniqueIndex() {
        snapshotRepository.saveAndFlush(row(2021, 5, null, true));

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(row(2021, 5, null, true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_tablero_snapshot_vigente");

        snapshotRepository.saveAndFlush(row(2021, 5, null, false));
        snapshotRepository.saveAndFlush(row(2021, 5, null, false));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2021 AND mes = 5 AND vigente", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2021 AND mes = 5", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("two current rows of the same building and month violate the index, different buildings do not")
    void sameBuildingTwiceViolatesUniqueIndex() {
        snapshotRepository.saveAndFlush(row(2021, 6, 10L, true));
        snapshotRepository.saveAndFlush(row(2021, 6, 11L, true));
        snapshotRepository.saveAndFlush(row(2021, 7, 10L, true));

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(row(2021, 6, 10L, true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_tablero_snapshot_vigente");
    }

    @Test
    @DisplayName("the stored payload of a building carries its counts, minutes, hourly profile and breakdown by source")
    void payloadHoldsRealData() throws Exception {
        jdbc.update("DELETE FROM feriado WHERE fecha BETWEEN '2022-05-01' AND '2022-05-31'");
        Building a = building();
        Classroom roomA = room(a, 30);
        allocated(roomA, LocalDate.of(2022, 5, 3), bandStart(), 120, 31, AllocationSource.MANUAL);
        allocated(roomA, LocalDate.of(2022, 5, 4), bandStart(), 60, 20, AllocationSource.IMPORTED);
        unallocated(LocalDate.of(2022, 5, 5), bandStart(), 60, 20);

        snapshotService.generate(YearMonth.of(2022, 5), "SISTEMA", null);

        JsonNode payload = payloadOf(YearMonth.of(2022, 5), a.getId());
        JsonNode parameters = payload.get("parameters");
        LocalTime hoursStart = LocalTime.parse(parameters.get("hoursStart").asText());
        LocalTime hoursEnd = LocalTime.parse(parameters.get("hoursEnd").asText());
        assertThat(hoursStart).isEqualTo(settingsReader.getTime(SettingKey.EVENTS_HOURS_START));
        assertThat(hoursEnd).isEqualTo(settingsReader.getTime(SettingKey.EVENTS_HOURS_END));
        assertThat(parameters.get("underusedThresholdPercent").asInt()).isEqualTo(50);
        assertThat(parameters.get("maxOverlapMinutes").asInt())
                .isEqualTo(settingsReader.getInt(SettingKey.ALLOCATION_MAX_OVERLAP_MINUTES));
        assertThat(payload.get("month").asText()).isEqualTo("2022-05");
        assertThat(payload.get("from").asText()).isEqualTo("2022-05-01");
        assertThat(payload.get("to").asText()).isEqualTo("2022-05-31");
        assertThat(payload.get("buildingName").asText()).isEqualTo(a.getName());
        assertThat(payload.get("roomCount").asInt()).isEqualTo(1);
        int availableDays = 26; // 31 days minus 5 Sundays, no holidays
        assertThat(payload.get("availableDays").asInt()).isEqualTo(availableDays);
        JsonNode categories = payload.get("categories");
        assertThat(categories.get("overcrowded").asLong()).isEqualTo(1);
        assertThat(categories.get("adequate").asLong()).isEqualTo(1);
        assertThat(categories.get("overlap").asLong()).isZero();
        assertThat(categories.get("underused").asLong()).isZero();
        assertThat(payload.get("occupiedRoomMinutes").asLong()).isEqualTo(180);
        long bandMinutes = Duration.between(hoursStart, hoursEnd).toMinutes();
        assertThat(payload.get("availableRoomMinutes").asLong()).isEqualTo(availableDays * bandMinutes);
        JsonNode profile = payload.get("hourlyProfile");
        assertThat(profile.get(0).get("hour").asInt()).isEqualTo(hoursStart.getHour());
        assertThat(profile.get(0).get("occupiedRoomMinutes").asLong()).isEqualTo(120);
        assertThat(profile.get(1).get("occupiedRoomMinutes").asLong()).isEqualTo(60);
        assertThat(profile.get(0).get("availableRoomMinutes").asLong()).isEqualTo(availableDays * 60L);
        JsonNode bySource = payload.get("bySource");
        assertThat(bySource.get(0).get("source").asText()).isEqualTo("MANUAL");
        assertThat(bySource.get(0).get("categories").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(bySource.get(2).get("source").asText()).isEqualTo("IMPORTED");
        assertThat(bySource.get(2).get("categories").get("adequate").asLong()).isEqualTo(1);
        assertThat(payload.get("unallocatedClasses").isNull()).isTrue();
        assertThat(payload.get("roomRequests").get("pending").asLong()).isZero();
        assertThat(payloadOf(YearMonth.of(2022, 5), null).get("unallocatedClasses").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a stored snapshot does not change when enrolled counts or capacities change afterwards")
    void storedSnapshotIsImmutable() throws Exception {
        jdbc.update("DELETE FROM feriado WHERE fecha BETWEEN '2022-06-01' AND '2022-06-30'");
        LocalDate day = LocalDate.of(2022, 6, 7);
        Building a = building();
        Classroom roomA = room(a, 30);
        var placed = allocated(roomA, day, bandStart(), 60, 20);
        snapshotService.generate(YearMonth.of(2022, 6), "SISTEMA", null);
        String reportBefore = mockMvc.perform(get("/v1/dashboard/reports/2022-06"))
                .andReturn().getResponse().getContentAsString();
        String payloadBefore = jdbc.queryForObject(
                "SELECT datos::text FROM tablero_snapshot WHERE id_edificio = ? AND vigente", String.class, a.getId());
        JsonNode liveBefore = getJson(mockMvc, "/v1/dashboard/week", "weekOf", day.toString());
        assertThat(byId(liveBefore.get("buildings"), "buildingId", a.getId())
                .get("allocationStatus").get("adequate").asLong()).isEqualTo(1);

        jdbc.update("UPDATE evento_academico SET cantidad_inscriptos = 40 WHERE id_evento_academico = ?", placed.eventId());
        jdbc.update("UPDATE aula SET capacidad = 10 WHERE id_aula = ?", roomA.getId());

        JsonNode liveAfter = getJson(mockMvc, "/v1/dashboard/week", "weekOf", day.toString());
        assertThat(byId(liveAfter.get("buildings"), "buildingId", a.getId())
                .get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        String reportAfter = mockMvc.perform(get("/v1/dashboard/reports/2022-06"))
                .andReturn().getResponse().getContentAsString();
        String payloadAfter = jdbc.queryForObject(
                "SELECT datos::text FROM tablero_snapshot WHERE id_edificio = ? AND vigente", String.class, a.getId());
        assertThat(payloadAfter).isEqualTo(payloadBefore);
        assertThat(reportAfter).isEqualTo(reportBefore);
        JsonNode reportBuilding = byId(objectMapper.readTree(reportAfter).get("buildings"), "buildingId", a.getId());
        assertThat(reportBuilding.get("allocationStatus").get("adequate").asLong()).isEqualTo(1);
        assertThat(reportBuilding.get("allocationStatus").get("overcrowded").asLong()).isZero();
    }

    @Test
    @DisplayName("the report shows the parameters stored in the snapshot, not the current ones")
    void reportReadsStoredParameters() throws Exception {
        snapshotService.generate(MONTH, "SISTEMA", null);
        jdbc.update("UPDATE tablero_snapshot SET datos = jsonb_set(datos, '{parameters,underusedThresholdPercent}', '80') "
                + "WHERE anio = 2020 AND mes = 3 AND id_edificio IS NULL");

        JsonNode report = getJson(mockMvc, "/v1/dashboard/reports/2020-03");
        JsonNode live = getJson(mockMvc, "/v1/dashboard/week");

        assertThat(report.get("parameters").get("underusedThresholdPercent").asInt()).isEqualTo(80);
        assertThat(live.get("parameters").get("underusedThresholdPercent").asInt()).isEqualTo(50);
    }
}
