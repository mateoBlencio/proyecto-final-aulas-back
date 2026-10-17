package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Liberación con motivo (integración): alcance por edificio, espejos y validaciones")
class ReleaseScopeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceService occurrenceService;

    private record Scenario(Building buildingA, Building buildingB, Long occurrenceInA, Long occurrenceInB,
                            Classroom classroomA) {}

    private Long singleOccurrenceAllocatedIn(Classroom classroom, LocalDate date) throws Exception {
        var sc = testData.materiaYComision();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(),
                LocalTime.of(18, 0), 90, date, date, 30);
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "],\"classroomId\":"
                                + classroom.getId() + "}]}"))
                .andExpect(status().isCreated());
        return occurrenceId;
    }

    private Scenario twoBuildings() throws Exception {
        Building a = testData.edificio();
        Building b = testData.edificio();
        Classroom classroomA = testData.aula(a);
        Classroom classroomB = testData.aula(b);
        LocalDate date = LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        return new Scenario(a, b, singleOccurrenceAllocatedIn(classroomA, date),
                singleOccurrenceAllocatedIn(classroomB, date), classroomA);
    }

    private MockMvc auxiliaryOf(Building building) {
        return mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    private static String releaseBody(Long occurrenceId, String reason, String note) {
        return "{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "]}],\"reason\":\"" + reason + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
    }

    private static String targetBody(Long occurrenceId) {
        return "{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "]}]}";
    }

    private void assertUntouched(Long occurrenceId) {
        Occurrence occurrence = occurrenceRepository.findById(occurrenceId).orElseThrow();
        assertThat(occurrence.getStatus()).isNotEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(allocationRepository.findByOccurrenceId(occurrenceId)).isPresent();
    }

    @Test
    @DisplayName("un auxiliar con alcance a un edificio recibe 403 al liberar una clase de otro y no se toca nada")
    void auxiliaryCannotReleaseOtherBuilding() throws Exception {
        Scenario s = twoBuildings();

        auxiliaryOf(s.buildingA()).perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(s.occurrenceInB(), "VIRTUAL_CLASS", null)))
                .andExpect(status().isForbidden());

        assertUntouched(s.occurrenceInB());
    }

    @Test
    @DisplayName("el auxiliar libera una clase de su edificio y despues la revierte aunque ya no tenga aula")
    void auxiliaryReleasesAndRevertsOwnBuilding() throws Exception {
        Scenario s = twoBuildings();
        MockMvc auxiliary = auxiliaryOf(s.buildingA());

        auxiliary.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(s.occurrenceInA(), "VIRTUAL_CLASS", null)))
                .andExpect(status().isOk());
        Occurrence released = occurrenceRepository.findById(s.occurrenceInA()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(released.getPreviousClassroomId()).isEqualTo(s.classroomA().getId());
        assertThat(allocationRepository.findByOccurrenceId(s.occurrenceInA())).isEmpty();

        auxiliary.perform(delete("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(targetBody(s.occurrenceInA())))
                .andExpect(status().isOk());

        Occurrence reverted = occurrenceRepository.findById(s.occurrenceInA()).orElseThrow();
        assertThat(reverted.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(reverted.getReleaseReason()).isNull();
    }

    @Test
    @DisplayName("un auxiliar recibe 403 al revertir una liberada de otro edificio y sigue liberada")
    void auxiliaryCannotRevertReleasedOfOtherBuilding() throws Exception {
        Scenario s = twoBuildings();
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(s.occurrenceInB(), "CANCELLED", null)))
                .andExpect(status().isOk());

        auxiliaryOf(s.buildingA()).perform(delete("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(targetBody(s.occurrenceInB())))
                .andExpect(status().isForbidden());

        Occurrence stillReleased = occurrenceRepository.findById(s.occurrenceInB()).orElseThrow();
        assertThat(stillReleased.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(stillReleased.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED);
    }

    @Test
    @DisplayName("liberar una ocurrencia pasada responde 409 y no cambia nada")
    void releasePastOccurrenceReturns409() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        var sc = testData.materiaYComision();
        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                .dayOfWeek(yesterday.getDayOfWeek()).startDate(yesterday).endDate(yesterday)
                .subjectId(sc.subjectId()).commissionId(sc.commissionId()).build());
        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(event).date(yesterday).status(OccurrenceStatus.NEEDS_ROOM).build());

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(past.getId(), "VIRTUAL_CLASS", null)))
                .andExpect(status().isConflict());

        assertThat(occurrenceRepository.findById(past.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("liberar la principal libera tambien sus espejos con el mismo motivo y borra sus asignaciones")
    void releasingPrincipalReleasesMirrors() throws Exception {
        Building building = testData.edificio();
        Classroom first = testData.aula(building);
        Classroom second = testData.aula(building);
        LocalDate date = LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long principalId = singleOccurrenceAllocatedIn(first, date);
        Long mirrorId = occurrenceService.createSimultaneous(principalId, 1).getFirst();
        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + mirrorId + "],\"classroomId\":"
                                + second.getId() + "}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(principalId, "OTHER", "obra en el edificio")))
                .andExpect(status().isOk());

        for (Long id : List.of(principalId, mirrorId)) {
            Occurrence occurrence = occurrenceRepository.findById(id).orElseThrow();
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
            assertThat(occurrence.getReleaseReason()).isEqualTo(ReleaseReason.OTHER);
            assertThat(occurrence.getReleaseNote()).isEqualTo("obra en el edificio");
            assertThat(allocationRepository.findByOccurrenceId(id)).isEmpty();
        }
        assertThat(occurrenceRepository.findById(mirrorId).orElseThrow().getPreviousClassroomId())
                .isEqualTo(second.getId());
    }

    @Test
    @DisplayName("OTHER sin nota y MIRROR_RETIRED responden 400 y no cambian nada")
    void invalidReasonsReturn400() throws Exception {
        Scenario s = twoBuildings();

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(s.occurrenceInA(), "OTHER", null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(s.occurrenceInA(), "MIRROR_RETIRED", "nota")))
                .andExpect(status().isBadRequest());

        assertUntouched(s.occurrenceInA());
    }

    // OBSOLETO: prueba del camino viejo, pendiente de revisión por Matías antes de borrar.
    // @Test
    // @DisplayName("POST /events/occurrences/{id}/release deprecado: 403 al auxiliar con alcance parcial, 204 a SUBSECRETARIA")
    // void deprecatedReleaseRequiresUnrestrictedScope() throws Exception {
    //     Scenario s = twoBuildings();
    //
    //     auxiliaryOf(s.buildingA()).perform(post("/v1/events/occurrences/{id}/release", s.occurrenceInA()))
    //             .andExpect(status().isForbidden());
    //     assertUntouched(s.occurrenceInA());
    //
    //     mockMvc.perform(post("/v1/events/occurrences/{id}/release", s.occurrenceInA()))
    //             .andExpect(status().isNoContent());
    //     assertThat(occurrenceRepository.findById(s.occurrenceInA()).orElseThrow().getStatus())
    //             .isEqualTo(OccurrenceStatus.ROOM_RELEASED);
    // }
    //
    // @Test
    // @DisplayName("POST /events/occurrences/{id}/request-room deprecado: 403 al auxiliar con alcance parcial, 204 a SUBSECRETARIA")
    // void deprecatedRequestRoomRequiresUnrestrictedScope() throws Exception {
    //     Scenario s = twoBuildings();
    //     mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
    //                     .content(releaseBody(s.occurrenceInA(), "VIRTUAL_CLASS", null)))
    //             .andExpect(status().isOk());
    //
    //     auxiliaryOf(s.buildingA()).perform(post("/v1/events/occurrences/{id}/request-room", s.occurrenceInA()))
    //             .andExpect(status().isForbidden());
    //     assertThat(occurrenceRepository.findById(s.occurrenceInA()).orElseThrow().getStatus())
    //             .isEqualTo(OccurrenceStatus.ROOM_RELEASED);
    //
    //     mockMvc.perform(post("/v1/events/occurrences/{id}/request-room", s.occurrenceInA()))
    //             .andExpect(status().isNoContent());
    //     assertThat(occurrenceRepository.findById(s.occurrenceInA()).orElseThrow().getStatus())
    //             .isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    // }
}
