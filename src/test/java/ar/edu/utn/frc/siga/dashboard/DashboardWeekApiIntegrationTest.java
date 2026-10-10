package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard - GET /v1/dashboard/week (integración)")
class DashboardWeekApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ObjectMapper objectMapper;

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

        String body = mockMvc.perform(get("/v1/dashboard/week").param("weekOf", monday.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(root.get("currentWeek").asBoolean()).isFalse();
        JsonNode mine = null;
        for (JsonNode node : root.get("buildings")) {
            if (node.get("buildingId").asLong() == building.getId()) {
                mine = node;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("allocationStatus").get("overcrowded").asLong()).isEqualTo(1);
        assertThat(mine.get("allocationStatus").get("total").asLong()).isEqualTo(1);
        assertThat(mine.get("occupancy").get("occupiedHours").asDouble()).isEqualTo(1.0);
        assertThat(mine.get("occupancy").get("availableHours").asDouble()).isEqualTo(15.0 * 6);
        assertThat(mine.get("hourlyProfile")).hasSize(15);
        assertThat(mine.get("roomsInUseNow").isNull()).isTrue();
        assertThat(mine.get("openRoomRequests").get("count").asLong()).isZero();
    }
}
