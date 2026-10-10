package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@Import({IntegrationTestData.class, DashboardWeekApiIntegrationTest.FixedClockConfig.class})
@DisplayName("Dashboard - GET /v1/dashboard/week (integración)")
class DashboardWeekApiIntegrationTest extends DashboardIntegrationSupport {

    /** Wednesday at 12:00 in a week no other test touches: "now" is fixed so the in-use test never skips. */
    private static final LocalDateTime FIXED_NOW = week(20).plusDays(2).atTime(12, 0);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.systemDefault();
            return Clock.fixed(FIXED_NOW.atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    private AcademicEventService academicEventService;

    private JsonNode weekOf(LocalDate day) throws Exception {
        return getJson(mockMvc, "/v1/dashboard/week", "weekOf", day.toString());
    }

    /** Days of Monday to Saturday that count in the response: six minus the excluded ones. */
    private static int availableDays(JsonNode root) {
        return 6 - root.get("excludedDays").size();
    }

    @Test
    @DisplayName("a building with one overcrowded one-hour class shows its category and occupied hours")
    void buildingWithOneOvercrowdedClass() throws Exception {
        LocalDate monday = LocalDate.now().plusDays(70).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building, testData.tipoAulaPorDefecto(), 30);
        var sc = testData.materiaYComision();
        Long eventId = academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                31, LocalTime.of(9, 0), 60, DayOfWeek.MONDAY, monday, monday, sc.subjectId(), sc.commissionId())).id();
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(classroom.getId()).source(AllocationSource.MANUAL).build());

        JsonNode root = weekOf(monday.plusDays(2));

        assertThat(root.get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(root.get("currentWeek").asBoolean()).isFalse();
        JsonNode mine = byId(root.get("buildings"), "buildingId", building.getId());
        assertThat(mine).isNotNull();
        assertThat(mine.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(mine.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(mine.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
        // The events.hours setting may have been changed by another test: expectations come from the response.
        LocalTime hoursStart = LocalTime.parse(root.get("parameters").get("hoursStart").asText());
        LocalTime hoursEnd = LocalTime.parse(root.get("parameters").get("hoursEnd").asText());
        double bandHours = Duration.between(hoursStart, hoursEnd).toMinutes() / 60.0;
        assertThat(mine.get("occupancy").get("availableHours").asDouble()).isEqualTo(bandHours * 6);
        assertThat(mine.get("hourlyProfile")).hasSize(hoursEnd.minusMinutes(1).getHour() - hoursStart.getHour() + 1);
        assertThat(mine.get("roomsInUseNow").isNull()).isTrue();
        assertThat(mine.get("openRoomRequests").get("count").asLong()).isZero();
    }

    @Test
    @DisplayName("two buildings are classified and measured separately and the campus adds them up")
    void twoBuildingsAreMeasuredSeparately() throws Exception {
        LocalDate monday = week(1);
        Building a = building();
        Building b = building();
        Classroom roomA = room(a, 30);
        Classroom roomB = room(b, 100);
        allocated(roomA, monday, bandStart(), 60, 31);
        allocated(roomB, monday.plusDays(1), bandStart(), 120, 60);
        allocated(roomB, monday.plusDays(2), bandStart(), 60, 10);

        JsonNode root = weekOf(monday);

        JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
        JsonNode nodeB = byId(root.get("buildings"), "buildingId", b.getId());
        double days = availableDays(root);
        assertThat(nodeA.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(nodeA.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(nodeA.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
        assertThat(nodeA.get("occupancy").get("availableHours").asDouble()).isEqualTo(bandHours() * days);
        assertThat(nodeB.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(nodeB.get("allocationStatus").get("adequate").asLong()).isEqualTo(1);
        assertThat(nodeB.get("allocationStatus").get("underused").asLong()).isEqualTo(1);
        assertThat(nodeB.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(3.0);
        JsonNode campus = root.get("campus");
        assertThat(campus.get("allocationStatus").get("total").asLong()).isEqualTo(3);
        assertThat(campus.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(campus.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(4.0);
        double buildingAvailable = 0;
        for (JsonNode building : root.get("buildings")) {
            buildingAvailable += building.get("occupancy").get("availableHours").asDouble();
        }
        assertThat(campus.get("occupancy").get("availableHours").asDouble()).isEqualTo(buildingAvailable);
        JsonNode firstSlot = nodeA.get("hourlyProfile").get(0);
        assertThat(firstSlot.get("hour").asInt()).isEqualTo(bandStart().getHour());
        assertThat(firstSlot.get("occupiedRoomMinutes").asLong()).isEqualTo(60);
        assertThat(firstSlot.get("availableRoomMinutes").asLong())
                .isEqualTo(60L * availableDays(root));
    }

    @Test
    @DisplayName("an event dropped from Sysacad (hash present, not enabled) is not counted, one without hash is")
    void eventDroppedFromSysacadIsNotCounted() throws Exception {
        LocalDate monday = week(2);
        Building a = building();
        Classroom roomA = room(a, 100);
        allocated(roomA, monday, bandStart(), 60, 50);
        var dropped = allocated(roomA, monday.plusDays(1), bandStart(), 60, 50);
        var droppedThenEnabled = allocated(roomA, monday.plusDays(2), bandStart(), 60, 50);
        dropFromSysacad(dropped.eventId());
        dropFromSysacad(droppedThenEnabled.eventId());
        jdbc.update("UPDATE evento_academico SET habilitado_sysacad = TRUE WHERE id_evento_academico = ?",
                droppedThenEnabled.eventId());
        unallocated(monday.plusDays(3), bandStart(), 60, 20);
        dropFromSysacad(unallocated(monday.plusDays(4), bandStart(), 60, 20).eventId());

        JsonNode root = weekOf(monday);

        JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
        assertThat(nodeA.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(nodeA.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(2.0);
        assertThat(root.get("campus").get("unallocatedClasses").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("an occurrence with the room released is not counted as allocated nor as unallocated")
    void releasedOccurrenceIsNotCounted() throws Exception {
        LocalDate monday = week(3);
        Building a = building();
        Classroom roomA = room(a, 100);
        allocated(roomA, monday, bandStart(), 60, 50);
        releaseRoom(allocated(roomA, monday.plusDays(1), bandStart(), 60, 50).occurrenceId());
        unallocated(monday.plusDays(2), bandStart(), 60, 20);
        releaseRoom(unallocated(monday.plusDays(3), bandStart(), 60, 20).occurrenceId());

        JsonNode root = weekOf(monday);

        JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
        assertThat(nodeA.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(root.get("campus").get("unallocatedClasses").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a deactivated building is left out of the response and of the campus totals")
    void deactivatedBuildingIsLeftOut() throws Exception {
        LocalDate monday = week(4);
        Building active = building();
        Building deactivated = building();
        allocated(room(active, 100), monday, bandStart(), 60, 50);
        allocated(room(deactivated, 100), monday.plusDays(1), bandStart(), 60, 50);
        deactivateBuilding(deactivated);

        JsonNode root = weekOf(monday);

        assertThat(byId(root.get("buildings"), "buildingId", deactivated.getId())).isNull();
        assertThat(byId(root.get("buildings"), "buildingId", active.getId())).isNotNull();
        assertThat(root.get("campus").get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(root.get("campus").get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a holiday lowers the available hours, shows up in excludedDays and its classes are not counted")
    void holidayInTheWeekIsExcluded() throws Exception {
        LocalDate monday = week(5);
        LocalDate holiday = monday.plusDays(2);
        Building a = building();
        Classroom roomA = room(a, 100);
        allocated(roomA, holiday, bandStart(), 60, 50);
        allocated(roomA, monday.plusDays(3), bandStart(), 60, 50);
        jdbc.update("INSERT INTO feriado (fecha, descripcion, sincronizado_en) VALUES (?, 'Test holiday', now())", holiday);
        try {
            JsonNode root = weekOf(monday);

            JsonNode excluded = root.get("excludedDays");
            assertThat(excluded).hasSize(1);
            assertThat(excluded.get(0).get("date").asText()).isEqualTo(holiday.toString());
            assertThat(excluded.get(0).get("reason").asText()).isEqualTo("HOLIDAY");
            assertThat(excluded.get(0).get("description").asText()).isEqualTo("Test holiday");
            JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
            assertThat(nodeA.get("occupancy").get("availableHours").asDouble()).isEqualTo(bandHours() * 5);
            assertThat(nodeA.get("allocationStatus").get("total").asLong()).isEqualTo(1);
            assertThat(nodeA.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
        } finally {
            jdbc.update("DELETE FROM feriado WHERE fecha = ?", holiday);
        }
    }

    @Test
    @DisplayName("an auxiliar sees only its building, the campus adds up its buildings and unallocated is null")
    void auxiliarIsScopedToItsBuilding() throws Exception {
        LocalDate monday = week(6);
        Building a = building();
        Building b = building();
        allocated(room(a, 30), monday, bandStart(), 60, 31);
        allocated(room(b, 100), monday, bandStart(), 120, 60);
        unallocated(monday.plusDays(1), bandStart(), 60, 20);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, b.getId(), null, null);

        JsonNode root = getJson(auxiliarOf(a), "/v1/dashboard/week", "weekOf", monday.toString());
        JsonNode global = weekOf(monday);

        assertThat(root.get("restrictedScope").asBoolean()).isTrue();
        assertThat(root.get("buildings")).hasSize(1);
        assertThat(root.get("buildings").get(0).get("buildingId").asLong()).isEqualTo(a.getId());
        JsonNode campus = root.get("campus");
        assertThat(campus.get("unallocatedClasses").isNull()).isTrue();
        assertThat(campus.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(campus.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(campus.get("occupancy")).isEqualTo(root.get("buildings").get(0).get("occupancy"));
        assertThat(campus.get("openRoomRequests").get("count").asLong()).isEqualTo(1);
        assertThat(global.get("restrictedScope").asBoolean()).isFalse();
        assertThat(global.get("campus").get("unallocatedClasses").asLong()).isEqualTo(1);
        assertThat(global.get("campus").get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(global.get("campus").get("openRoomRequests").get("count").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("the current week counts the rooms with a class in progress right now")
    void currentWeekShowsRoomsInUseNow() throws Exception {
        LocalDateTime nowMinute = FIXED_NOW;
        LocalDate today = nowMinute.toLocalDate();
        Building a = building();
        Building b = building();
        Classroom running = room(a, 100);
        room(a, 100); // idle room: counts in the total but not in use
        Classroom endedEarlier = room(b, 100);
        allocated(running, today, nowMinute.toLocalTime().minusMinutes(5), 60, 50);
        allocated(endedEarlier, today, nowMinute.toLocalTime().minusMinutes(130), 60, 50);

        JsonNode root = getJson(mockMvc, "/v1/dashboard/week");

        assertThat(root.get("currentWeek").asBoolean()).isTrue();
        JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
        JsonNode nodeB = byId(root.get("buildings"), "buildingId", b.getId());
        assertThat(nodeA.get("roomsInUseNow").get("inUse").asInt()).isEqualTo(1);
        assertThat(nodeA.get("roomsInUseNow").get("total").asInt()).isEqualTo(2);
        assertThat(nodeB.get("roomsInUseNow").get("inUse").asInt()).isZero();
        assertThat(nodeB.get("roomsInUseNow").get("total").asInt()).isEqualTo(1);
        int inUse = 0;
        int total = 0;
        for (JsonNode building : root.get("buildings")) {
            inUse += building.get("roomsInUseNow").get("inUse").asInt();
            total += building.get("roomsInUseNow").get("total").asInt();
        }
        assertThat(root.get("campus").get("roomsInUseNow").get("inUse").asInt()).isEqualTo(inUse);
        assertThat(root.get("campus").get("roomsInUseNow").get("total").asInt()).isEqualTo(total);
        assertThat(inUse).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a week without events answers 200 with zero counters and null percentages")
    void weekWithoutDataIsEmpty() throws Exception {
        LocalDate monday = week(9);
        Building a = building();
        room(a, 30);

        JsonNode root = weekOf(monday);

        JsonNode nodeA = byId(root.get("buildings"), "buildingId", a.getId());
        assertThat(nodeA.get("allocationStatus").get("total").asLong()).isZero();
        assertThat(nodeA.get("allocationStatus").get("overlapPercent").isNull()).isTrue();
        assertThat(nodeA.get("occupancy").get("occupiedHours").asDouble()).isZero();
        assertThat(nodeA.get("occupancy").get("occupancyPercent").asDouble()).isZero();
        assertThat(root.get("campus").get("allocationStatus").get("total").asLong()).isZero();
        assertThat(root.get("campus").get("unallocatedClasses").asLong()).isZero();
        assertThat(root.get("campus").get("occupancy").get("occupiedHours").asDouble()).isZero();
    }

    @Test
    @DisplayName("any day from Monday to Sunday maps to the same Monday-to-Saturday week; the next Monday starts the next one")
    void weekBoundaries() throws Exception {
        LocalDate monday = week(10);

        assertThat(weekOf(monday).get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(weekOf(monday.plusDays(5)).get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(weekOf(monday.plusDays(6)).get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(weekOf(monday.plusDays(7)).get("weekStart").asText()).isEqualTo(monday.plusDays(7).toString());
        assertThat(weekOf(monday.minusDays(1)).get("weekStart").asText()).isEqualTo(monday.minusDays(7).toString());
        assertThat(weekOf(monday).get("weekEnd").asText()).isEqualTo(monday.plusDays(5).toString());
    }

    @Test
    @DisplayName("CONSULTA reads the week, a user without roles gets 403")
    void permissions() throws Exception {
        MockMvc consulta = consulta();
        consulta.perform(get("/v1/dashboard/week")).andExpect(status().isOk());

        userWithoutRoles().perform(get("/v1/dashboard/week")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("weekOf too far from today answers 400 instead of overflowing")
    void weekOfOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(get("/v1/dashboard/week").param("weekOf", "+999999999-12-31")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard/week").param("weekOf", "1990-01-01")).andExpect(status().isBadRequest());
    }
}
