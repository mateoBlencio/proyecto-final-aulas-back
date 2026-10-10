package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Campus counters are shared with every other test: unrestricted checks are a delta against a baseline read before
 * the fixtures, per-building checks use fresh buildings.
 */
@Import(IntegrationTestData.class)
@DisplayName("Dashboard - GET /v1/dashboard/room-requests (integración)")
class DashboardRoomRequestsApiIntegrationTest extends DashboardIntegrationSupport {

    private static final int THIS_YEAR = LocalDate.now().getYear();

    private JsonNode year(MockMvc mvc, int year, String yearsParam) throws Exception {
        JsonNode years = getJson(mvc, "/v1/dashboard/room-requests", "years", yearsParam).get("years");
        return byId(years, "year", (long) year);
    }

    private JsonNode thisYear(MockMvc mvc) throws Exception {
        return year(mvc, THIS_YEAR, "5");
    }

    /** Counter of a counts node (campus or unassignedBuilding) of this year, 0 if the year has no row yet. */
    private static long count(JsonNode yearNode, String section, String counter) {
        if (yearNode == null) {
            return 0;
        }
        JsonNode value = yearNode.get(section).get(counter);
        return value.isNull() ? 0 : value.asLong();
    }

    private static JsonNode buildingRow(JsonNode yearNode, Building building) {
        return byId(yearNode.get("buildings"), "buildingId", building.getId());
    }

    @Test
    @DisplayName("a pending item shows up in the current year, marked partial")
    void pendingItemAppearsInCurrentYear() throws Exception {
        var sc = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, sc.subjectId());
        testData.itemDePedido(request, sc.commissionId(), LocalDate.now().plusDays(30), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        JsonNode current = thisYear(mockMvc);

        assertThat(current.get("partial").asBoolean()).isTrue();
        assertThat(current.get("campus").get("pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(current.get("unassignedBuilding").get("pending").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("an item resolved with rooms in two buildings counts once in each building and once in the campus")
    void resolvedItemWithRoomsInTwoBuildings() throws Exception {
        Building a = building();
        Building b = building();
        Classroom a1 = room(a, 30);
        Classroom a2 = room(a, 30);
        Classroom b1 = room(b, 30);
        long campusBefore = count(thisYear(mockMvc), "campus", "resolved");
        Long spansBoth = item(RoomRequestStatus.NEW, null, null, null);
        Long onlyInA = item(RoomRequestStatus.NEW, null, null, null);
        resolve(spansBoth, OffsetDateTime.now());
        resolve(onlyInA, OffsetDateTime.now());
        assignRoom(spansBoth, a1, 1);
        assignRoom(spansBoth, b1, 2);
        assignRoom(onlyInA, a1, 1);
        assignRoom(onlyInA, a2, 2);

        JsonNode current = thisYear(mockMvc);

        assertThat(buildingRow(current, a).get("resolved").asLong()).isEqualTo(2);
        assertThat(buildingRow(current, b).get("resolved").asLong()).isEqualTo(1);
        assertThat(count(current, "campus", "resolved") - campusBefore).isEqualTo(2);
    }

    @Test
    @DisplayName("an item cancelled by SISTEMA counts as expired, one cancelled by a person does not")
    void expiredCountsOnlyCancellationsBySystem() throws Exception {
        Building a = building();
        long campusBefore = count(thisYear(mockMvc), "campus", "expired");
        long unassignedBefore = count(thisYear(mockMvc), "unassignedBuilding", "expired");
        Long bySystem = item(RoomRequestStatus.NEW, a.getId(), null, null);
        Long byPerson = item(RoomRequestStatus.NEW, a.getId(), null, null);
        Long bySystemWithoutBuilding = item(RoomRequestStatus.NEW, null, null, null);
        cancel(bySystem, "SISTEMA", OffsetDateTime.now());
        cancel(byPerson, "subsecretaria@frc.utn.edu.ar", OffsetDateTime.now());
        cancel(bySystemWithoutBuilding, "SISTEMA", OffsetDateTime.now());

        JsonNode current = thisYear(mockMvc);

        assertThat(buildingRow(current, a).get("expired").asLong()).isEqualTo(1);
        assertThat(count(current, "campus", "expired") - campusBefore).isEqualTo(2);
        assertThat(count(current, "unassignedBuilding", "expired") - unassignedBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("the not-derived row is visible for an unrestricted user and null for a restricted one")
    void notDerivedRowDependsOnTheScope() throws Exception {
        Building a = building();
        MockMvc auxA = auxiliarOf(a);
        long unassignedBefore = count(thisYear(mockMvc), "unassignedBuilding", "pending");
        long campusBefore = count(thisYear(mockMvc), "campus", "pending");
        item(RoomRequestStatus.NEW, null, null, null);
        item(RoomRequestStatus.DERIVED_TO_BUILDING, a.getId(), null, null);

        JsonNode global = thisYear(mockMvc);
        JsonNode restricted = thisYear(auxA);

        assertThat(count(global, "unassignedBuilding", "pending") - unassignedBefore).isEqualTo(1);
        assertThat(count(global, "campus", "pending") - campusBefore).isEqualTo(2);
        assertThat(restricted.get("unassignedBuilding").isNull()).isTrue();
        assertThat(restricted.get("buildings")).hasSize(1);
        assertThat(buildingRow(restricted, a).get("pending").asLong()).isEqualTo(1);
        assertThat(restricted.get("campus").get("pending").asLong()).isEqualTo(1);
        assertThat(getJson(auxA, "/v1/dashboard/room-requests").get("restrictedScope").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a year without any count is omitted and past years carry no pending counter")
    void yearWithoutCountsIsOmitted() throws Exception {
        Building a = building();
        Classroom a1 = room(a, 30);
        Long resolvedTwoYearsAgo = item(RoomRequestStatus.NEW, null, null, null);
        resolve(resolvedTwoYearsAgo, OffsetDateTime.now().minusYears(2));
        assignRoom(resolvedTwoYearsAgo, a1, 1);

        JsonNode years = getJson(mockMvc, "/v1/dashboard/room-requests", "years", "5").get("years");

        assertThat(byId(years, "year", (long) THIS_YEAR - 1)).isNull();
        JsonNode twoYearsAgo = byId(years, "year", (long) THIS_YEAR - 2);
        assertThat(twoYearsAgo).isNotNull();
        assertThat(twoYearsAgo.get("partial").asBoolean()).isFalse();
        assertThat(twoYearsAgo.get("campus").get("pending").isNull()).isTrue();
        assertThat(buildingRow(twoYearsAgo, a).get("resolved").asLong()).isEqualTo(1);
        long previous = Long.MIN_VALUE;
        for (JsonNode node : years) {
            assertThat(node.get("year").asLong()).isGreaterThan(previous);
            previous = node.get("year").asLong();
        }
    }

    @Test
    @DisplayName("an item resolved three years ago shows up with years=4 and not with years=3")
    void yearsParameterLimitsTheRange() throws Exception {
        Long resolvedThreeYearsAgo = item(RoomRequestStatus.NEW, null, null, null);
        resolve(resolvedThreeYearsAgo, OffsetDateTime.now().minusYears(3));

        assertThat(year(mockMvc, THIS_YEAR - 3, "4")).isNotNull();
        assertThat(year(mockMvc, THIS_YEAR - 3, "3")).isNull();
    }

    @Test
    @DisplayName("years outside 1..10 returns 400")
    void yearsOutOfRangeIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "11")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "1")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "10")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CONSULTA reads the stats, a user without roles gets 403")
    void permissions() throws Exception {
        consulta().perform(get("/v1/dashboard/room-requests")).andExpect(status().isOk());

        userWithoutRoles().perform(get("/v1/dashboard/room-requests")).andExpect(status().isForbidden());
    }
}
