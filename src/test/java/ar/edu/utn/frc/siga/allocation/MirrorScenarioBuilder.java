package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Arma una clase de una sola fecha con la principal y sus espejos asignados, cada uno en su aula propia. */
final class MirrorScenarioBuilder {

    record Scenario(Long eventId, LocalDate date, Long principalId, Classroom principalRoom, List<Long> mirrorIds,
                    List<Classroom> mirrorRooms) {

        Long mirrorId() {
            return mirrorIds.getFirst();
        }

        Classroom mirrorRoom() {
            return mirrorRooms.getFirst();
        }
    }

    private final IntegrationTestData testData;
    private final OccurrenceRepository occurrenceRepository;
    private final OccurrenceService occurrenceService;
    private final MockMvc mockMvc;

    MirrorScenarioBuilder(IntegrationTestData testData, OccurrenceRepository occurrenceRepository,
            OccurrenceService occurrenceService, MockMvc mockMvc) {
        this.testData = testData;
        this.occurrenceRepository = occurrenceRepository;
        this.occurrenceService = occurrenceService;
        this.mockMvc = mockMvc;
    }

    static LocalDate mondayOf(int yearsAhead, int weekOffset) {
        return LocalDate.now().plusYears(yearsAhead).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(weekOffset);
    }

    Scenario build(LocalDate monday, Building principalBuilding, Building mirrorBuilding, int... mirrorCapacities)
            throws Exception {
        List<Classroom> mirrorRooms = new ArrayList<>();
        for (int capacity : mirrorCapacities) {
            mirrorRooms.add(testData.aula(mirrorBuilding, testData.tipoAulaPorDefecto(), capacity));
        }
        return buildIn(monday, testData.aula(principalBuilding), mirrorRooms);
    }

    /** Clase con la principal en {@code principalRoom} y un espejo en cada aula de {@code mirrorRooms}. */
    Scenario buildIn(LocalDate monday, Classroom principalRoom, List<Classroom> mirrorRooms) throws Exception {
        var sc = testData.materiaYComision();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, monday, monday, 30);
        Long principalId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        List<Long> mirrorIds = occurrenceService.createSimultaneous(principalId, mirrorRooms.size());

        assign(principalId, principalRoom);
        for (int i = 0; i < mirrorRooms.size(); i++) {
            assign(mirrorIds.get(i), mirrorRooms.get(i));
        }
        return new Scenario(eventId, monday, principalId, principalRoom, mirrorIds, mirrorRooms);
    }

    Scenario build(LocalDate monday, int... mirrorCapacities) throws Exception {
        Building building = testData.edificio();
        return build(monday, building, building, mirrorCapacities);
    }

    private void assign(Long occurrenceId, Classroom room) throws Exception {
        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "],\"classroomId\":"
                                + room.getId() + "}]}"))
                .andExpect(status().isCreated());
    }
}
