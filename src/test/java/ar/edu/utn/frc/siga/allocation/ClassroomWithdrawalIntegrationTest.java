package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.MirrorScenarioBuilder.Scenario;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Retiro de aula de las clases futuras (integración)")
class ClassroomWithdrawalIntegrationTest extends AbstractIntegrationTest {

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
        return MirrorScenarioBuilder.mondayOf(4, WEEK.incrementAndGet());
    }

    private ResultActions withdraw(MockMvc as, Long classroomId, LocalDate from) throws Exception {
        String fromJson = from == null ? "" : ",\"from\":\"" + from + "\"";
        return as.perform(post("/v1/allocations/classroom-withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"classroomId\":" + classroomId + fromJson + ",\"note\":\"inundacion\"}"));
    }

    private ResultActions withdraw(Long classroomId) throws Exception {
        return withdraw(mockMvc, classroomId, null);
    }

    private Occurrence occurrence(Long id) {
        return occurrenceRepository.findById(id).orElseThrow();
    }

    private Long classroomOf(Long occurrenceId) {
        return allocationRepository.findByOccurrenceId(occurrenceId).map(Allocation::getClassroomId).orElse(null);
    }

    private void assertUntouched(Scenario s) {
        assertThat(classroomOf(s.principalId())).isEqualTo(s.principalRoom().getId());
        assertThat(classroomOf(s.mirrorId())).isEqualTo(s.mirrorRoom().getId());
        assertThat(occurrence(s.principalId()).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(occurrence(s.mirrorId()).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    private void softDeleteClassroom(Classroom classroom) {
        classroomRepository.softDelete(classroomRepository.findById(classroom.getId()).orElseThrow());
    }

    private record Mixed(Classroom withdrawn, Scenario consolidated, Scenario mirrorInWithdrawn, Scenario alone) {}

    /** Aula R con una principal con espejo, un espejo de otra clase y una principal sin espejo. */
    private Mixed mixedScenario() throws Exception {
        Building building = testData.edificio();
        Classroom withdrawn = testData.aula(building);
        Classroom other = testData.aula(building);
        Scenario consolidated = scenarios.buildIn(nextDate(), withdrawn, List.of(other));
        Scenario mirrorInWithdrawn = scenarios.buildIn(nextDate(), testData.aula(building), List.of(withdrawn));
        Scenario alone = scenarios.buildIn(nextDate(), withdrawn, List.of());
        return new Mixed(withdrawn, consolidated, mirrorInWithdrawn, alone);
    }

    @Test
    @DisplayName("una llamada devuelve consolidated, mirrorsRetired y withoutAlternative y aplica cada cambio")
    void returnsThreeListsAndAppliesChanges() throws Exception {
        Mixed m = mixedScenario();

        withdraw(m.withdrawn().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated.length()").value(1))
                .andExpect(jsonPath("$.consolidated[0].occurrenceId").value(m.consolidated().principalId().intValue()))
                .andExpect(jsonPath("$.consolidated[0].eventId").value(m.consolidated().eventId().intValue()))
                .andExpect(jsonPath("$.consolidated[0].date").value(m.consolidated().date().toString()))
                .andExpect(jsonPath("$.consolidated[0].fromClassroomId").value(m.withdrawn().getId().intValue()))
                .andExpect(jsonPath("$.consolidated[0].toClassroomId")
                        .value(m.consolidated().mirrorRoom().getId().intValue()))
                .andExpect(jsonPath("$.mirrorsRetired.length()").value(1))
                .andExpect(jsonPath("$.mirrorsRetired[0].occurrenceId")
                        .value(m.mirrorInWithdrawn().mirrorId().intValue()))
                .andExpect(jsonPath("$.mirrorsRetired[0].principalOccurrenceId")
                        .value(m.mirrorInWithdrawn().principalId().intValue()))
                .andExpect(jsonPath("$.withoutAlternative.length()").value(1))
                .andExpect(jsonPath("$.withoutAlternative[0].occurrenceId").value(m.alone().principalId().intValue()));

        Scenario c = m.consolidated();
        assertThat(classroomOf(c.principalId())).isEqualTo(c.mirrorRoom().getId());
        assertThat(allocationRepository.findByOccurrenceId(c.principalId()).orElseThrow().getSource())
                .isEqualTo(AllocationSource.MANUAL);
        assertThat(classroomOf(c.mirrorId())).isNull();
        Occurrence retiredByConsolidation = occurrence(c.mirrorId());
        assertThat(retiredByConsolidation.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(retiredByConsolidation.getReleaseReason()).isEqualTo(ReleaseReason.MIRROR_RETIRED);
        assertThat(retiredByConsolidation.getReleaseNote()).startsWith("Retiro del aula ").endsWith(": inundacion");

        Scenario r = m.mirrorInWithdrawn();
        assertThat(classroomOf(r.mirrorId())).isNull();
        Occurrence retired = occurrence(r.mirrorId());
        assertThat(retired.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(retired.getReleaseReason()).isEqualTo(ReleaseReason.MIRROR_RETIRED);
        assertThat(retired.getPreviousClassroomId()).isEqualTo(m.withdrawn().getId());
        assertThat(classroomOf(r.principalId())).isEqualTo(r.principalRoom().getId());

        assertThat(classroomOf(m.alone().principalId())).isEqualTo(m.withdrawn().getId());
    }

    @Test
    @DisplayName("una segunda llamada solo encuentra lo que no tiene alternativa")
    void secondCallOnlyFindsWithoutAlternative() throws Exception {
        Mixed m = mixedScenario();
        withdraw(m.withdrawn().getId()).andExpect(status().isOk());

        withdraw(m.withdrawn().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated").isEmpty())
                .andExpect(jsonPath("$.mirrorsRetired").isEmpty())
                .andExpect(jsonPath("$.withoutAlternative.length()").value(1))
                .andExpect(jsonPath("$.withoutAlternative[0].occurrenceId").value(m.alone().principalId().intValue()));
    }

    @Test
    @DisplayName("las clases anteriores a 'from' no se tocan y las de esa fecha en adelante si")
    void classesBeforeFromAreNotTouched() throws Exception {
        Building building = testData.edificio();
        Classroom withdrawn = testData.aula(building);
        Classroom other = testData.aula(building);
        Scenario before = scenarios.buildIn(nextDate(), withdrawn, List.of(other));
        LocalDate from = nextDate();
        Scenario onFrom = scenarios.buildIn(from, withdrawn, List.of(testData.aula(building)));

        withdraw(mockMvc, withdrawn.getId(), from).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated.length()").value(1))
                .andExpect(jsonPath("$.consolidated[0].occurrenceId").value(onFrom.principalId().intValue()));

        assertUntouched(before);
    }

    @Test
    @DisplayName("una clase de hoy que ya empezó no se toca")
    void classAlreadyStartedTodayIsNotTouched() throws Exception {
        var sc = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom withdrawn = testData.aula(building);
        Classroom other = testData.aula(building);
        LocalDate today = LocalDate.now();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), today.getDayOfWeek(),
                LocalTime.of(0, 0), 30, today, today, 30);
        Long principalId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        Long mirrorId = occurrenceService.createSimultaneous(principalId, 1).getFirst();
        allocationRepository.save(Allocation.builder().occurrenceId(principalId).classroomId(withdrawn.getId())
                .source(AllocationSource.MANUAL).build());
        allocationRepository.save(Allocation.builder().occurrenceId(mirrorId).classroomId(other.getId())
                .source(AllocationSource.MANUAL).build());

        withdraw(withdrawn.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated").isEmpty())
                .andExpect(jsonPath("$.mirrorsRetired").isEmpty())
                .andExpect(jsonPath("$.withoutAlternative").isEmpty());

        assertThat(classroomOf(principalId)).isEqualTo(withdrawn.getId());
        assertThat(classroomOf(mirrorId)).isEqualTo(other.getId());
        assertThat(occurrence(mirrorId).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("si el aula ya se dio de baja antes del retiro no queda nada que mover y responde 200 con listas vacías")
    void classroomDeactivatedBeforeWithdrawalFindsNothing() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);
        mockMvc.perform(delete("/v1/classrooms/{id}/activation", s.principalRoom().getId()))
                .andExpect(status().isOk());
        Long principalClassroomAfterDeactivation = classroomOf(s.principalId());

        withdraw(s.principalRoom().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated").isEmpty())
                .andExpect(jsonPath("$.mirrorsRetired").isEmpty())
                .andExpect(jsonPath("$.withoutAlternative").isEmpty());

        assertThat(classroomOf(s.principalId())).isEqualTo(principalClassroomAfterDeactivation);
    }

    @Test
    @DisplayName("si el aula del espejo no está disponible la principal queda en withoutAlternative sin cambios")
    void unavailableMirrorClassroomMeansWithoutAlternative() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);
        softDeleteClassroom(s.mirrorRoom());

        withdraw(s.principalRoom().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidated").isEmpty())
                .andExpect(jsonPath("$.mirrorsRetired").isEmpty())
                .andExpect(jsonPath("$.withoutAlternative.length()").value(1))
                .andExpect(jsonPath("$.withoutAlternative[0].occurrenceId").value(s.principalId().intValue()));

        assertUntouched(s);
    }

    @Test
    @DisplayName("un 'from' anterior a hoy responde 400")
    void fromInThePastRespondsBadRequest() throws Exception {
        Scenario s = scenarios.build(nextDate(), 40);

        withdraw(mockMvc, s.principalRoom().getId(), LocalDate.now().minusDays(1)).andExpect(status().isBadRequest());

        assertUntouched(s);
    }

    @Test
    @DisplayName("un aula inexistente responde 404")
    void unknownClassroomRespondsNotFound() throws Exception {
        withdraw(Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un usuario sin alcance sobre el edificio del aula destino recibe 403 y no cambia nada")
    void userWithoutScopeOverTargetBuildingGets403() throws Exception {
        Building own = testData.edificio();
        Building foreign = testData.edificio();
        Scenario s = scenarios.build(nextDate(), own, foreign, 40);
        MockMvc auxiliar = mockMvcAsScoped("aux-retiro-aula@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO,
                ScopeType.BUILDING, own.getId());

        withdraw(auxiliar, s.principalRoom().getId(), null).andExpect(status().isForbidden());

        assertUntouched(s);
    }
}
