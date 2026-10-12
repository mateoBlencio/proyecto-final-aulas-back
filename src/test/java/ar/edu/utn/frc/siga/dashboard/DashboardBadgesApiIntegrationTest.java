package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * The campus counters are shared with every other test, so unrestricted users are checked as a delta against a
 * baseline read before the fixtures exist. Users restricted to a fresh building see only that building's data.
 */
@Import(IntegrationTestData.class)
@DisplayName("Dashboard - GET /v1/dashboard/badges (integración)")
class DashboardBadgesApiIntegrationTest extends DashboardIntegrationSupport {

    private JsonNode badges(MockMvc mvc) throws Exception {
        return getJson(mvc, "/v1/dashboard/badges");
    }

    /** The first day from {@code from} on that is not a Sunday (Sundays never count). */
    private static LocalDate workday(LocalDate from) {
        return from.getDayOfWeek() == DayOfWeek.SUNDAY ? from.plusDays(1) : from;
    }

    @Test
    @DisplayName("responds the four counters for an unrestricted user")
    void unrestrictedUserGetsAllCounters() throws Exception {
        mockMvc.perform(get("/v1/dashboard/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomRequestsAwaitingAction").isNumber())
                .andExpect(jsonPath("$.roomRequestsExpiringSoon").isNumber())
                .andExpect(jsonPath("$.unallocatedClassesNext7Days").isNumber())
                .andExpect(jsonPath("$.conflictsNext7Days").isNumber());
    }

    @Test
    @DisplayName("awaiting action: Subsecretaria sees new and non-derived in-evaluation items, each auxiliar the ones derived to its building")
    void awaitingActionDependsOnTheRole() throws Exception {
        Building a = building();
        Building b = building();
        MockMvc auxA = auxiliarOf(a);
        MockMvc auxB = auxiliarOf(b);
        long subsecretariaBefore = badges(mockMvc).get("roomRequestsAwaitingAction").asLong();

        item(RoomRequestStatus.NEW, null, null, null);
        item(RoomRequestStatus.IN_EVALUATION, null, null, null);
        item(RoomRequestStatus.IN_EVALUATION, a.getId(), null, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, b.getId(), null, null);
        item(RoomRequestStatus.RESOLVED, a.getId(), null, null);
        item(RoomRequestStatus.CANCELLED, null, null, null);

        assertThat(badges(mockMvc).get("roomRequestsAwaitingAction").asLong() - subsecretariaBefore).isEqualTo(2);
        assertThat(badges(auxA).get("roomRequestsAwaitingAction").asLong()).isEqualTo(2);
        assertThat(badges(auxB).get("roomRequestsAwaitingAction").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("expiring soon: an item starting in 47 hours counts, one in 49 hours, one without date and a past one do not")
    void expiringSoonWindowIsFortyEightHours() throws Exception {
        Building a = building();
        MockMvc auxA = auxiliarOf(a);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime in47 = now.plusHours(47);
        LocalDateTime in49 = now.plusHours(49);
        LocalDateTime past = now.minusHours(1);
        long before = badges(mockMvc).get("roomRequestsExpiringSoon").asLong();

        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), in47.toLocalDate(), in47.toLocalTime());
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), in49.toLocalDate(), in49.toLocalTime());
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), past.toLocalDate(), past.toLocalTime());

        assertThat(badges(auxA).get("roomRequestsExpiringSoon").asLong()).isEqualTo(1);
        assertThat(badges(mockMvc).get("roomRequestsExpiringSoon").asLong() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("expiring soon: a dated item without start time counts until the end of its day")
    void itemWithoutStartTimeCountsUntilEndOfDay() throws Exception {
        Building a = building();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate inThreeDays = LocalDate.now().plusDays(3);

        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), tomorrow, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), inThreeDays, null);

        assertThat(badges(auxiliarOf(a)).get("roomRequestsExpiringSoon").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a restricted user gets unallocatedClassesNext7Days null, an unrestricted one counts only the next 7 days")
    void unallocatedBadgeIsNullWhenRestricted() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate inside = workday(today.plusDays(5));
        LocalDate outside = workday(today.plusDays(7));
        long before = badges(mockMvc).get("unallocatedClassesNext7Days").asLong();

        unallocated(inside, bandStart(), 60, 20);
        unallocated(outside, bandStart(), 60, 20);

        assertThat(badges(mockMvc).get("unallocatedClassesNext7Days").asLong() - before).isEqualTo(1);
        assertThat(badges(auxiliarOf(building())).get("unallocatedClassesNext7Days").isNull()).isTrue();
    }

    @Test
    @DisplayName("conflicts: an overcrowded class in 3 days counts, one in 8 days does not; an overlapping pair counts twice")
    void conflictsWindowIsSevenDays() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate inside = workday(today.plusDays(3));
        LocalDate outside = workday(today.plusDays(8));
        Building a = building();
        Building b = building();
        Classroom smallRoom = room(a, 30);
        Classroom sharedRoom = room(b, 100);
        long before = badges(mockMvc).get("conflictsNext7Days").asLong();

        allocated(smallRoom, inside, bandStart(), 60, 31);
        allocated(smallRoom, outside, bandStart(), 60, 31);
        allocated(sharedRoom, inside, bandStart(), 120, 50);
        allocated(sharedRoom, inside, bandStart(), 120, 50);

        assertThat(badges(auxiliarOf(a)).get("conflictsNext7Days").asLong()).isEqualTo(1);
        assertThat(badges(auxiliarOf(b)).get("conflictsNext7Days").asLong()).isEqualTo(2);
        assertThat(badges(mockMvc).get("conflictsNext7Days").asLong() - before).isEqualTo(3);
    }

    @Test
    @DisplayName("CONSULTA gets the counters, a user without roles gets 403")
    void permissions() throws Exception {
        consulta().perform(get("/v1/dashboard/badges")).andExpect(status().isOk());

        userWithoutRoles().perform(get("/v1/dashboard/badges")).andExpect(status().isForbidden());
    }
}
