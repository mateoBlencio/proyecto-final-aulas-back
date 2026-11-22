package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * Today is Saturday 3500-05-12. Year 3500 is used by no other test. The ANUAL 3500 runs 03-01 to 11-30 with the
 * recess 07-05 to 07-17, so the first term is 03-01 to 07-04.
 */
@Import({IntegrationTestData.class, DashboardPeriodApiIntegrationTest.FixedClockConfig.class})
@DisplayName("Dashboard - GET /v1/dashboard/period (integración)")
class DashboardPeriodApiIntegrationTest extends DashboardIntegrationSupport {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime FIXED_NOW = LocalDate.of(3500, 5, 12).atTime(12, 0);
    private static final String PERIOD = "/v1/dashboard/period";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    private AcademicPeriodRepository periodRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void createAnnualPeriod() {
        AcademicPeriod annual = testData.periodoAcademico(3500, TermType.ANUAL);
        annual.setRecessStart(LocalDate.of(3500, 7, 5));
        annual.setRecessEnd(LocalDate.of(3500, 7, 17));
        periodRepository.save(annual);
    }

    /**
     * The shared fixture's commission only accepts classes within five years of the real date. This one lives in
     * a first-term period of year 3499 (not an ANUAL, so findAnnual(3499) stays empty) that spans 2000 to 3500, so classes can be placed in year 3500.
     */
    @Override
    protected IntegrationTestData.SubjectAndCommission sc() {
        if (subjectAndCommission == null) {
            var plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(),
                    testData.especialidad((int) IntegrationTestData.nextSeq()));
            var subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia-IT", plan,
                    TermType.ANUAL.getLabel());
            AcademicPeriod wide = testData.periodoAcademico(3499, TermType.PRIMER_CUATRIMESTRE);
            wide.setStartDate(LocalDate.of(2000, 1, 1));
            wide.setEndDate(LocalDate.of(3500, 12, 31));
            wide = periodRepository.save(wide);
            var commission = testData.comision("CUR-" + IntegrationTestData.nextSeq(), wide);
            testData.materiaComision(subject, commission, 30);
            subjectAndCommission = new IntegrationTestData.SubjectAndCommission(subject.getId(), commission.getId());
        }
        return subjectAndCommission;
    }

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(3500, month, dayOfMonth);
    }

    private static OffsetDateTime at(LocalDateTime local) {
        return local.atZone(ZONE).toOffsetDateTime();
    }

    private static LocalDateTime dateTime(int month, int dayOfMonth, int hour, int minute) {
        return LocalDateTime.of(3500, month, dayOfMonth, hour, minute);
    }

    private JsonNode period(String... params) throws Exception {
        return getJson(mockMvc, PERIOD, params);
    }

    private static JsonNode building(JsonNode root, Building building) {
        return byId(root.get("buildings"), "buildingId", building.getId());
    }

    /** A resolved, expired and pending count of a building, in the shape the response uses. */
    private static void assertRequests(JsonNode requests, long resolved, long expired, long pending) {
        assertThat(requests.get("resolved").asLong()).as("resolved").isEqualTo(resolved);
        assertThat(requests.get("expired").asLong()).as("expired").isEqualTo(expired);
        assertThat(requests.get("pending").asLong()).as("pending").isEqualTo(pending);
    }

    /**
     * Items of building {@code b} around the window 3500-03-01 to 3500-05-12: 2 resolved and 1 expired inside, 3
     * pending at the end of the last day, and 4 that count nowhere.
     */
    private void requestsAround(Building b, Classroom room) {
        // Resolved: last minute of the last day and first minute of the first day.
        resolvedAt(b, room, dateTime(5, 12, 23, 59));
        resolvedAt(b, room, dateTime(3, 1, 0, 0));
        // Resolved outside the window: the minute before the first day (never pending) and the first minute after
        // the last day (still open at the end of the last day, so pending).
        resolvedAt(b, room, dateTime(2, 28, 23, 59));
        resolvedAt(b, room, dateTime(5, 13, 0, 0));
        // Expired inside the window; expired the first minute after it (pending); cancelled by a person (counts nowhere).
        cancelledAt(b, "SISTEMA", dateTime(5, 12, 23, 59));
        cancelledAt(b, "SISTEMA", dateTime(5, 13, 0, 0));
        cancelledAt(b, "someone@frc.utn.edu.ar", dateTime(4, 1, 10, 0));
        // Open: created the last minute of the last day (pending) and the first minute after it (not yet created).
        Long open = item(RoomRequestStatus.DERIVED_TO_BUILDING, b.getId(), null, null);
        createdAt(open, dateTime(5, 12, 23, 59));
        Long notYetCreated = item(RoomRequestStatus.DERIVED_TO_BUILDING, b.getId(), null, null);
        createdAt(notYetCreated, dateTime(5, 13, 0, 0));
    }

    private void resolvedAt(Building b, Classroom room, LocalDateTime notifiedAt) {
        Long id = item(RoomRequestStatus.RESOLVED, b.getId(), null, null);
        assignRoom(id, room, 1);
        resolve(id, at(notifiedAt));
    }

    private void cancelledAt(Building b, String decidedBy, LocalDateTime decidedAt) {
        Long id = item(RoomRequestStatus.CANCELLED, b.getId(), null, null);
        cancel(id, decidedBy, at(decidedAt));
    }

    private void closedRequestsOfTwoYears(Building b, Classroom room) {
        resolvedAt(b, room, at3499(6, 1));
        resolvedAt(b, room, at3499(12, 15));
        cancelledAt(b, "SISTEMA", at3499(7, 1));
        item(RoomRequestStatus.DERIVED_TO_BUILDING, b.getId(), null, null);
    }

    private static LocalDateTime at3499(int month, int dayOfMonth) {
        return LocalDateTime.of(3499, month, dayOfMonth, 10, 0);
    }

    @Test
    @DisplayName("without type it answers the first term of the current year, up to today")
    void defaultTypeIsTerm() throws Exception {
        JsonNode root = period();

        assertThat(root.get("type").asText()).isEqualTo("TERM");
        assertThat(root.get("year").asInt()).isEqualTo(3500);
        assertThat(root.get("semester").asInt()).isEqualTo(1);
        assertThat(root.get("periodStart").asText()).isEqualTo("3500-03-01");
        assertThat(root.get("periodEnd").asText()).isEqualTo("3500-07-04");
        assertThat(root.get("from").asText()).isEqualTo("3500-03-01");
        assertThat(root.get("to").asText()).isEqualTo("3500-05-12");
        assertThat(root.get("partial").asBoolean()).isTrue();
        assertThat(root.get("restrictedScope").asBoolean()).isFalse();
        assertThat(period("type", "TERM")).isEqualTo(root);
    }

    @Test
    @DisplayName("allocations after today and before the term do not count, those on the first day and today do")
    void allocationsOutsideTheRangeAreNotCounted() throws Exception {
        Building a = building();
        Classroom room = room(a, 100);
        allocated(room, day(3, 1), bandStart(), 60, 50);
        allocated(room, day(5, 12), bandStart(), 60, 50);
        allocated(room, day(2, 28), bandStart(), 60, 50);
        allocated(room, day(5, 14), bandStart(), 60, 50);
        allocated(room, day(5, 19), bandStart(), 60, 50);

        JsonNode mine = building(period(), a);

        assertThat(mine.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(mine.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(2.0);
        assertThat(mine.get("availableDays").asInt()).isEqualTo(63);
    }

    @Test
    @DisplayName("resolved and expired are counted inside [from, to], pending at the end of the day to")
    void requestWindow() throws Exception {
        Building a = building();
        requestsAround(a, room(a, 100));

        JsonNode mine = building(period(), a);

        assertRequests(mine.get("roomRequests"), 2, 1, 3);
    }

    @Test
    @DisplayName("a holiday shows in excludedDays, lowers the available days and its classes are not counted")
    void holidayIsExcluded() throws Exception {
        Building a = building();
        Classroom room = room(a, 100);
        LocalDate holiday = day(4, 16);
        allocated(room, holiday, bandStart(), 60, 50);
        allocated(room, day(4, 17), bandStart(), 60, 50);
        jdbc.update("INSERT INTO feriado (fecha, descripcion, sincronizado_en) VALUES (?, 'Period test holiday', now())",
                holiday);
        try {
            JsonNode root = period();

            JsonNode excluded = root.get("excludedDays");
            assertThat(excluded).hasSize(1);
            assertThat(excluded.get(0).get("date").asText()).isEqualTo("3500-04-16");
            assertThat(excluded.get(0).get("reason").asText()).isEqualTo("HOLIDAY");
            assertThat(excluded.get(0).get("description").asText()).isEqualTo("Period test holiday");
            JsonNode mine = building(root, a);
            assertThat(mine.get("availableDays").asInt()).isEqualTo(62);
            assertThat(root.get("campus").get("availableDays").asInt()).isEqualTo(62);
            assertThat(mine.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM feriado WHERE fecha = ?", holiday);
        }
    }

    @Test
    @DisplayName("without holidays excludedDays is empty")
    void noHolidaysNoExcludedDays() throws Exception {
        assertThat(period().get("excludedDays")).isEmpty();
    }

    @Test
    @DisplayName("an auxiliar sees its building only; the campus is that building and the null fields stay null")
    void scopedUser() throws Exception {
        Building a = building();
        Building b = building();
        Classroom roomA = room(a, 100);
        Classroom roomB = room(b, 100);
        allocated(roomA, day(4, 2), bandStart(), 60, 50);
        allocated(roomA, day(4, 3), bandStart(), 60, 50);
        allocated(roomB, day(4, 2), bandStart(), 60, 50);
        unallocated(day(4, 4), bandStart(), 60, 20);
        requestsAround(a, roomA);
        requestsAround(b, roomB);
        requestsAround(b, roomB);

        JsonNode scoped = getJson(auxiliarOf(a), PERIOD);
        JsonNode global = period();

        assertThat(scoped.get("restrictedScope").asBoolean()).isTrue();
        assertThat(scoped.get("buildings")).hasSize(1);
        assertThat(scoped.get("buildings").get(0).get("buildingId").asLong()).isEqualTo(a.getId());
        JsonNode campus = scoped.get("campus");
        assertThat(campus.get("unallocatedClasses").isNull()).isTrue();
        assertThat(campus.get("unassignedBuildingRoomRequests").isNull()).isTrue();
        assertThat(campus.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(campus.get("occupancy")).isEqualTo(scoped.get("buildings").get(0).get("occupancy"));
        assertRequests(campus.get("roomRequests"), 2, 1, 3);
        assertThat(global.get("restrictedScope").asBoolean()).isFalse();
        assertThat(global.get("campus").get("unallocatedClasses").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(global.get("campus").get("unassignedBuildingRoomRequests").isNull()).isFalse();
        assertRequests(building(global, b).get("roomRequests"), 4, 2, 6);
    }

    @Test
    @DisplayName("an unknown type and a lowercase type answer 400 with a ProblemDetail")
    void invalidTypeIsRejected() throws Exception {
        for (String type : new String[] {"FOO", "term"}) {
            mockMvc.perform(get(PERIOD).param("type", type))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetro con tipo inválido"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    @DisplayName("CONSULTA reads the period, a user without roles gets 403 and a request without token gets 401")
    void permissions() throws Exception {
        consulta().perform(get(PERIOD)).andExpect(status().isOk());

        userWithoutRoles().perform(get(PERIOD)).andExpect(status().isForbidden());

        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        anonymous.perform(get(PERIOD)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an explicit past term comes whole, not partial, with TermType dates when the year has no ANUAL")
    void explicitPastTerm() throws Exception {
        Building a = building();
        Classroom room = room(a, 100);
        allocated(room, LocalDate.of(3499, 9, 12), bandStart(), 60, 60);
        allocated(room, LocalDate.of(3499, 12, 1), bandStart(), 60, 60);
        allocated(room, LocalDate.of(3499, 7, 31), bandStart(), 60, 60);

        JsonNode root = period("type", "TERM", "year", "3499", "semester", "2");

        assertThat(root.get("year").asInt()).isEqualTo(3499);
        assertThat(root.get("semester").asInt()).isEqualTo(2);
        assertThat(root.get("periodStart").asText()).isEqualTo("3499-08-01");
        assertThat(root.get("periodEnd").asText()).isEqualTo("3499-11-30");
        assertThat(root.get("to").asText()).isEqualTo("3499-11-30");
        assertThat(root.get("partial").asBoolean()).isFalse();
        assertThat(root.get("years").isNull()).isTrue();
        assertThat(building(root, a).get("allocationStatus").get("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("LAST_YEARS for a scoped user keeps unallocatedClasses null and the campus equal to its building")
    void lastYearsScoped() throws Exception {
        Building a = building();
        Classroom room = room(a, 100);
        allocated(room, LocalDate.of(3499, 4, 5), bandStart(), 60, 60);
        allocated(room, day(5, 12), bandStart(), 60, 60);
        unallocated(day(4, 2), bandStart(), 60, 20);
        closedRequestsOfTwoYears(a, room);

        JsonNode root = getJson(auxiliarOf(a), PERIOD, "type", "LAST_YEARS", "years", "2");

        assertThat(root.get("restrictedScope").asBoolean()).isTrue();
        assertThat(root.get("buildings")).hasSize(1);
        JsonNode campus = root.get("campus");
        assertThat(campus.get("unallocatedClasses").isNull()).isTrue();
        assertThat(campus.get("unassignedBuildingRoomRequests").isNull()).isTrue();
        assertThat(campus.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(campus.get("availableDays").asInt()).isEqualTo(236 + 63);
        assertRequests(campus.get("roomRequests"), 1, 1, 1);
    }

    @Test
    @DisplayName("the current term asked explicitly is the same as the default one")
    void explicitCurrentTerm() throws Exception {
        assertThat(period("type", "TERM", "year", "3500", "semester", "1")).isEqualTo(period());
    }

    @Test
    @DisplayName("a term that has not started answers 422")
    void futureTermIsRejected() throws Exception {
        mockMvc.perform(get(PERIOD).param("type", "TERM").param("year", "3500").param("semester", "2"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(PERIOD).param("type", "ACADEMIC_YEAR").param("year", "3501"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("invalid selections answer 400: year or semester alone, semester 3, years missing or out of range, "
            + "a year more than 10 years away and the removed CURRENT_MONTH")
    void invalidSelectionsAreRejected() throws Exception {
        String[][] bad = {
                {"type", "TERM", "year", "3500"},
                {"type", "TERM", "semester", "1"},
                {"type", "TERM", "year", "3500", "semester", "3"},
                {"type", "TERM", "year", "3500", "semester", "0"},
                {"type", "TERM", "year", "3511", "semester", "1"},
                {"type", "ACADEMIC_YEAR", "year", "3489"},
                {"type", "LAST_YEARS"},
                {"type", "LAST_YEARS", "years", "0"},
                {"type", "LAST_YEARS", "years", "11"},
                {"type", "CURRENT_MONTH"},
        };
        for (String[] params : bad) {
            var request = get(PERIOD);
            for (int i = 0; i < params.length; i += 2) {
                request.param(params[i], params[i + 1]);
            }
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("parameters that do not belong to the type are ignored")
    void irrelevantParametersAreIgnored() throws Exception {
        assertThat(period("type", "TERM", "years", "5")).isEqualTo(period());
        assertThat(period("type", "ACADEMIC_YEAR", "year", "3500", "semester", "2", "years", "5"))
                .isEqualTo(period("type", "ACADEMIC_YEAR", "year", "3500"));
    }

    @Test
    @DisplayName("ACADEMIC_YEAR with a past year: semester 0, the whole year, not partial")
    void academicYearPast() throws Exception {
        Building a = building();
        allocated(room(a, 100), LocalDate.of(3499, 4, 5), bandStart(), 60, 50);

        JsonNode root = period("type", "ACADEMIC_YEAR", "year", "3499");

        assertThat(root.get("type").asText()).isEqualTo("ACADEMIC_YEAR");
        assertThat(root.get("year").asInt()).isEqualTo(3499);
        assertThat(root.get("semester").asInt()).isZero();
        assertThat(root.get("years").isNull()).isTrue();
        assertThat(root.get("periodStart").asText()).isEqualTo("3499-03-01");
        assertThat(root.get("periodEnd").asText()).isEqualTo("3499-11-30");
        assertThat(root.get("to").asText()).isEqualTo("3499-11-30");
        assertThat(root.get("partial").asBoolean()).isFalse();
        assertThat(building(root, a).get("availableDays").asInt()).isEqualTo(236);
        assertThat(building(root, a).get("allocationStatus").get("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("ACADEMIC_YEAR without year is the ANUAL in progress, up to today")
    void academicYearDefault() throws Exception {
        JsonNode root = period("type", "ACADEMIC_YEAR");

        assertThat(root.get("year").asInt()).isEqualTo(3500);
        assertThat(root.get("semester").asInt()).isZero();
        assertThat(root.get("periodStart").asText()).isEqualTo("3500-03-01");
        assertThat(root.get("periodEnd").asText()).isEqualTo("3500-11-30");
        assertThat(root.get("to").asText()).isEqualTo("3500-05-12");
        assertThat(root.get("partial").asBoolean()).isTrue();
    }

    /**
     * Building {@code a} with two rooms and one class of a full band each, one in 3499 (overcrowded) and one in 3500
     * (underused), plus classes in the days between the two ANUAL and after today that must not count.
     */
    private void lastYearsAllocations(Building a) {
        Classroom small = room(a, 30);
        Classroom big = room(a, 100);
        allocated(small, LocalDate.of(3499, 4, 5), bandStart(), (int) bandMinutes(), 31);
        allocated(big, LocalDate.of(3500, 4, 5), bandStart(), (int) bandMinutes(), 10);
        allocated(small, LocalDate.of(3499, 12, 11), bandStart(), 60, 31);
        allocated(small, LocalDate.of(3500, 1, 10), bandStart(), 60, 31);
        allocated(small, LocalDate.of(3500, 5, 14), bandStart(), 60, 31);
    }

    private void lastYearsRequests(Building a, Classroom room) {
        resolvedAt(a, room, dateTime(6, 1, 10, 0).withYear(3499));
        resolvedAt(a, room, dateTime(4, 1, 10, 0));
        resolvedAt(a, room, dateTime(12, 15, 10, 0).withYear(3499));
        cancelledAt(a, "SISTEMA", dateTime(10, 1, 10, 0).withYear(3499));
        cancelledAt(a, "SISTEMA", dateTime(12, 20, 10, 0).withYear(3499));
        Long openSinceJanuary = item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        createdAt(openSinceJanuary, dateTime(1, 10, 10, 0));
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        Long afterCut = item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        createdAt(afterCut, dateTime(5, 13, 0, 0));
    }

    @Test
    @DisplayName("LAST_YEARS=2 adds the two ANUAL windows, leaves out the days between them and recomputes percentages")
    void lastYearsAddsTheWindows() throws Exception {
        Building a = building();
        lastYearsAllocations(a);
        lastYearsRequests(a, room(a, 100));
        long band = bandMinutes();

        JsonNode root = period("type", "LAST_YEARS", "years", "2");

        assertThat(root.get("type").asText()).isEqualTo("LAST_YEARS");
        assertThat(root.get("year").isNull()).isTrue();
        assertThat(root.get("semester").isNull()).isTrue();
        assertThat(root.get("years").asInt()).isEqualTo(2);
        assertThat(root.get("periodStart").asText()).isEqualTo("3499-03-01");
        assertThat(root.get("periodEnd").asText()).isEqualTo("3500-11-30");
        assertThat(root.get("from").asText()).isEqualTo("3499-03-01");
        assertThat(root.get("to").asText()).isEqualTo("3500-05-12");
        assertThat(root.get("partial").asBoolean()).isTrue();
        JsonNode mine = building(root, a);
        assertThat(mine.get("availableDays").asInt()).isEqualTo(299);
        assertThat(mine.get("roomCount").asInt()).isEqualTo(3);
        assertThat(mine.get("allocationStatus").get("total").asLong()).isEqualTo(2);
        assertThat(mine.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(mine.get("allocationStatus").get("underused").asLong()).isEqualTo(1);
        assertThat(mine.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(DashboardRules.hours(2 * band));
        assertThat(mine.get("occupancy").get("availableHours").asDouble())
                .isEqualTo(DashboardRules.hours(band * 299 * 3));
        // 2 bands over 3 rooms x 299 days x 1 band: 0.223%. The mean of the yearly percentages (0.1 and 0.5) is 0.3.
        assertThat(mine.get("occupancy").get("occupancyPercent").asDouble()).isEqualTo(0.2);
        assertRequests(mine.get("roomRequests"), 2, 1, 2);
    }

    @Test
    @DisplayName("LAST_YEARS=2 equals the sum of the two ACADEMIC_YEAR it covers, except pending and room count")
    void lastYearsEqualsTheSumOfAcademicYears() throws Exception {
        Building a = building();
        lastYearsAllocations(a);
        lastYearsRequests(a, room(a, 100));

        JsonNode last = building(period("type", "LAST_YEARS", "years", "2"), a);
        JsonNode first = building(period("type", "ACADEMIC_YEAR", "year", "3499"), a);
        JsonNode second = building(period("type", "ACADEMIC_YEAR", "year", "3500"), a);

        assertThat(last.get("availableDays").asInt())
                .isEqualTo(first.get("availableDays").asInt() + second.get("availableDays").asInt());
        for (String field : new String[] {"total", "overcrowded", "underused", "adequate", "overlap"}) {
            assertThat(last.get("allocationStatus").get(field).asLong()).as(field)
                    .isEqualTo(first.get("allocationStatus").get(field).asLong()
                            + second.get("allocationStatus").get(field).asLong());
        }
        for (String field : new String[] {"occupiedHours", "availableHours"}) {
            assertThat(last.get("occupancy").get(field).asDouble()).as(field)
                    .isCloseTo(first.get("occupancy").get(field).asDouble()
                            + second.get("occupancy").get(field).asDouble(), org.assertj.core.api.Assertions.offset(0.11));
        }
        for (String field : new String[] {"resolved", "expired"}) {
            assertThat(last.get("roomRequests").get(field).asLong()).as(field)
                    .isEqualTo(first.get("roomRequests").get(field).asLong()
                            + second.get("roomRequests").get(field).asLong());
        }
        assertThat(last.get("roomCount").asInt()).isEqualTo(second.get("roomCount").asInt());
        JsonNode lastSlot = last.get("hourlyProfile").get(0);
        assertThat(lastSlot.get("occupiedRoomMinutes").asLong())
                .isEqualTo(first.get("hourlyProfile").get(0).get("occupiedRoomMinutes").asLong()
                        + second.get("hourlyProfile").get(0).get("occupiedRoomMinutes").asLong());
        assertThat(lastSlot.get("availableRoomMinutes").asLong())
                .isEqualTo(first.get("hourlyProfile").get(0).get("availableRoomMinutes").asLong()
                        + second.get("hourlyProfile").get(0).get("availableRoomMinutes").asLong());
        assertThat(last.get("roomRequests").get("pending").asLong())
                .isEqualTo(second.get("roomRequests").get("pending").asLong());
    }

    @Test
    @DisplayName("LAST_YEARS adds the unallocated classes of both windows and leaves out the ones after today")
    void lastYearsCampus() throws Exception {
        Building a = building();
        Building b = building();
        allocated(room(a, 100), LocalDate.of(3499, 4, 5), bandStart(), 60, 50);
        allocated(room(b, 100), LocalDate.of(3500, 4, 5), bandStart(), 60, 50);
        long before = period("type", "LAST_YEARS", "years", "2").get("campus").get("unallocatedClasses").asLong();
        unallocated(LocalDate.of(3499, 5, 5), bandStart(), 60, 20);
        unallocated(LocalDate.of(3500, 4, 4), bandStart(), 60, 20);
        unallocated(LocalDate.of(3500, 5, 14), bandStart(), 60, 20);

        JsonNode root = period("type", "LAST_YEARS", "years", "2");

        assertThat(root.get("campus").get("unallocatedClasses").asLong()).isEqualTo(before + 2);
        assertThat(building(root, a)).isNotNull();
        assertThat(building(root, b)).isNotNull();
    }

    @Test
    @DisplayName("LAST_YEARS lists the buildings ordered by code, those without code last")
    void lastYearsBuildingsOrderedByCode() throws Exception {
        building();
        building();

        JsonNode buildings = period("type", "LAST_YEARS", "years", "2").get("buildings");

        int previous = Integer.MIN_VALUE;
        boolean seenNull = false;
        for (JsonNode node : buildings) {
            if (node.get("code").isNull()) {
                seenNull = true;
                continue;
            }
            assertThat(seenNull).as("a building with code after one without").isFalse();
            assertThat(node.get("code").asInt()).isGreaterThanOrEqualTo(previous);
            previous = node.get("code").asInt();
        }
        assertThat(buildings.size()).isGreaterThanOrEqualTo(2);
    }
}
