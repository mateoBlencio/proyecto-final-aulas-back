package ar.edu.utn.frc.siga.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("Registros existentes que apuntan a materias o aulas dadas de baja siguen mostrando su nombre (integración)")
class HistoricalReferencesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private StudyPlanRepository studyPlanRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomRepository classroomRepository;

    private Long createEvent(IntegrationTestData.SubjectAndCommission sc) {
        LocalDate date = LocalDate.now().plusDays(14);
        return academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                30, LocalTime.of(10, 0), 90, date.getDayOfWeek(), date, date,
                sc.subjectId(), sc.commissionId())).id();
    }

    @Test
    @DisplayName("un evento cuya materia se inhabilita sigue devolviendo la materia")
    void eventOfInactiveSubject_stillReturnsSubject() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Long eventId = createEvent(sc);
        Subject subject = subjectRepository.findById(sc.subjectId()).orElseThrow();
        subject.deactivate();
        subjectRepository.save(subject);

        mockMvc.perform(get("/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject.id").value(sc.subjectId()))
                .andExpect(jsonPath("$.subject.name").value(subject.getName()));
    }

    @Test
    @DisplayName("un evento cuya materia queda oculta por la baja de su plan sigue devolviendo la materia")
    void eventOfSubjectInInactivePlan_stillReturnsSubject() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Long eventId = createEvent(sc);
        Subject subject = subjectRepository.findById(sc.subjectId()).orElseThrow();
        StudyPlan plan = studyPlanRepository.findById(subject.getStudyPlan().getId()).orElseThrow();
        plan.deactivate();
        studyPlanRepository.save(plan);

        mockMvc.perform(get("/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject.id").value(sc.subjectId()));
        mockMvc.perform(get("/v1/events").param("subjectId", String.valueOf(sc.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(eventId.intValue())))
                .andExpect(jsonPath("$.content[0].subject.id").value(sc.subjectId()));
    }

    @Test
    @DisplayName("una asignación a un aula de un edificio inhabilitado sigue devolviendo el aula con su edificio")
    void allocationToClassroomOfInactiveBuilding_stillReturnsClassroom() {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Long eventId = createEvent(sc);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.saveAndFlush(Allocation.builder()
                .occurrenceId(occurrence.getId())
                .classroomId(classroom.getId())
                .source(AllocationSource.MANUAL)
                .build());
        building.deactivate();
        buildingRepository.save(building);

        List<AllocationResponseDto> allocations = asFixtureUser(
                () -> allocationService.findByOccurrenceIds(List.of(occurrence.getId())));

        assertThat(allocations).hasSize(1);
        assertThat(allocations.getFirst().classroom()).isNotNull();
        assertThat(allocations.getFirst().classroom().id()).isEqualTo(classroom.getId());
        assertThat(allocations.getFirst().classroom().buildingName()).isEqualTo(building.getName());
    }

    @Test
    @DisplayName("una asignación a un aula inhabilitada por sí misma sigue devolviendo el aula")
    void allocationToInactiveClassroom_stillReturnsClassroom() {
        Classroom classroom = testData.aula(testData.edificio());
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Long eventId = createEvent(sc);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.saveAndFlush(Allocation.builder()
                .occurrenceId(occurrence.getId())
                .classroomId(classroom.getId())
                .source(AllocationSource.MANUAL)
                .build());
        classroom.deactivate();
        classroomRepository.save(classroom);

        List<AllocationResponseDto> allocations = asFixtureUser(
                () -> allocationService.findByOccurrenceIds(List.of(occurrence.getId())));

        assertThat(allocations).hasSize(1);
        assertThat(allocations.getFirst().classroom()).isNotNull();
        assertThat(allocations.getFirst().classroom().id()).isEqualTo(classroom.getId());
    }
}
