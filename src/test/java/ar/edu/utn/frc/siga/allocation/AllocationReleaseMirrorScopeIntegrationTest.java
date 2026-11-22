package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.MirrorScenarioBuilder.Scenario;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Liberación con motivo y alcance sobre espejos (integración)")
class AllocationReleaseMirrorScopeIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger WEEK = new AtomicInteger();

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private OccurrenceService occurrenceService;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ClassroomRepository classroomRepository;

    private MirrorScenarioBuilder scenarios;

    @BeforeEach
    void setUpScenarios() {
        scenarios = new MirrorScenarioBuilder(testData, occurrenceRepository, occurrenceService, mockMvc);
    }

    private LocalDate nextDate() {
        return MirrorScenarioBuilder.mondayOf(3, WEEK.incrementAndGet());
    }

    private ResultActions release(String occurrenceIds, String mirrorScope, String note) throws Exception {
        String scope = mirrorScope == null ? "" : ",\"mirrorScope\":\"" + mirrorScope + "\"";
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceIds + "]}],\"reason\":\"VIRTUAL_CLASS\""
                        + noteJson + scope + "}"));
    }

    private Occurrence occurrence(Long id) {
        return occurrenceRepository.findById(id).orElseThrow();
    }

    private void assertReleasedAs(Long id, ReleaseReason reason, Long previousClassroomId) {
        Occurrence occurrence = occurrence(id);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(occurrence.getReleaseReason()).isEqualTo(reason);
        assertThat(occurrence.getPreviousClassroomId()).isEqualTo(previousClassroomId);
        assertThat(allocationRepository.findByOccurrenceId(id)).isEmpty();
    }

    private void assertStillAssigned(Long id, Long classroomId) {
        assertThat(occurrence(id).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(allocationRepository.findByOccurrenceId(id)).map(Allocation::getClassroomId).contains(classroomId);
    }

    @Test
    @DisplayName("sin mirrorScope, liberar la principal libera también al espejo, como siempre")
    void withoutMirrorScopeReleasesBoth() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        release("" + s.principalId(), null, "cursado virtual").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        assertReleasedAs(s.principalId(), ReleaseReason.VIRTUAL_CLASS, s.principalRoom().getId());
        assertReleasedAs(s.mirrorId(), ReleaseReason.VIRTUAL_CLASS, s.mirrorRoom().getId());
    }

    @Test
    @DisplayName("ALL libera principal y espejo y no informa ningún movimiento")
    void allReleasesBoth() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        release("" + s.principalId(), "ALL", "cursado virtual").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].movedToClassroomId").doesNotExist())
                .andExpect(jsonPath("$[1].movedToClassroomId").doesNotExist());

        assertReleasedAs(s.principalId(), ReleaseReason.VIRTUAL_CLASS, s.principalRoom().getId());
        assertReleasedAs(s.mirrorId(), ReleaseReason.VIRTUAL_CLASS, s.mirrorRoom().getId());
    }

    @Test
    @DisplayName("ONLY_SELECTED sobre la principal: pasa al aula del espejo con origen MANUAL y el espejo queda retirado")
    void onlySelectedOnPrincipalConsolidatesIntoMirror() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        release("" + s.principalId(), "ONLY_SELECTED", "cursado virtual").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].occurrenceId").value(s.principalId().intValue()))
                .andExpect(jsonPath("$[0].freedClassroomId").value(s.principalRoom().getId().intValue()))
                .andExpect(jsonPath("$[0].movedToClassroomId").value(s.mirrorRoom().getId().intValue()))
                .andExpect(jsonPath("$[?(@.occurrenceId==" + s.mirrorId() + ")]").isEmpty());

        assertStillAssigned(s.principalId(), s.mirrorRoom().getId());
        assertThat(allocationRepository.findByOccurrenceId(s.principalId()).orElseThrow().getSource())
                .isEqualTo(AllocationSource.MANUAL);
        assertReleasedAs(s.mirrorId(), ReleaseReason.MIRROR_RETIRED, s.mirrorRoom().getId());
        assertThat(occurrence(s.mirrorId()).getReleaseNote()).isEqualTo("Liberación parcial (Clase virtual): cursado virtual");
    }

    @Test
    @DisplayName("ONLY_SELECTED sobre el espejo: solo se libera el espejo y la principal sigue en su aula")
    void onlySelectedOnMirrorReleasesOnlyMirror() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        release("" + s.mirrorId(), "ONLY_SELECTED", "cursado virtual").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].occurrenceId").value(s.mirrorId().intValue()))
                .andExpect(jsonPath("$[0].movedToClassroomId").doesNotExist());

        assertReleasedAs(s.mirrorId(), ReleaseReason.VIRTUAL_CLASS, s.mirrorRoom().getId());
        assertStillAssigned(s.principalId(), s.principalRoom().getId());
    }

    @Test
    @DisplayName("con dos espejos gana el de aula más grande aunque tenga mayor roomSlot; el otro sigue asignado")
    void biggestMirrorClassroomWins() throws Exception {
        Scenario s = scenarios.build(nextDate(), 30, 80);

        release("" + s.principalId(), "ONLY_SELECTED", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movedToClassroomId").value(s.mirrorRooms().get(1).getId().intValue()));

        assertStillAssigned(s.principalId(), s.mirrorRooms().get(1).getId());
        assertReleasedAs(s.mirrorIds().get(1), ReleaseReason.MIRROR_RETIRED, s.mirrorRooms().get(1).getId());
        assertStillAssigned(s.mirrorIds().get(0), s.mirrorRooms().get(0).getId());
    }

    @Test
    @DisplayName("si el aula del único espejo no está disponible responde 409 con las fechas y no cambia nada")
    void unavailableMirrorClassroomRespondsConflictWithoutChanges() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);
        classroomRepository.softDelete(classroomRepository.findById(s.mirrorRoom().getId()).orElseThrow());

        release("" + s.principalId(), "ONLY_SELECTED", "cursado virtual").andExpect(status().isConflict())
                .andExpect(jsonPath("$.dates[0]").value(s.date().toString()));

        assertStillAssigned(s.principalId(), s.principalRoom().getId());
        assertStillAssigned(s.mirrorId(), s.mirrorRoom().getId());
        assertThat(occurrence(s.principalId()).getReleaseReason()).isNull();
        assertThat(occurrence(s.mirrorId()).getReleaseReason()).isNull();
    }

    @Test
    @DisplayName("una nota de usuario de 255 caracteres deja la nota del espejo cortada a 255")
    void mirrorNoteIsTruncatedTo255() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);
        String longNote = "x".repeat(255);

        release("" + s.principalId(), "ONLY_SELECTED", longNote).andExpect(status().isOk());

        String note = occurrence(s.mirrorId()).getReleaseNote();
        assertThat(note).hasSize(255).startsWith("Liberación parcial (Clase virtual): xxx");
    }

    @Test
    @DisplayName("con el evento completo como target, ONLY_SELECTED libera principal y espejo sin consolidar")
    void wholeEventTargetReleasesEverything() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + s.eventId() + "}],\"reason\":\"VIRTUAL_CLASS\","
                                + "\"mirrorScope\":\"ONLY_SELECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].movedToClassroomId").doesNotExist())
                .andExpect(jsonPath("$[1].movedToClassroomId").doesNotExist());

        assertReleasedAs(s.principalId(), ReleaseReason.VIRTUAL_CLASS, s.principalRoom().getId());
        assertReleasedAs(s.mirrorId(), ReleaseReason.VIRTUAL_CLASS, s.mirrorRoom().getId());
    }
}
