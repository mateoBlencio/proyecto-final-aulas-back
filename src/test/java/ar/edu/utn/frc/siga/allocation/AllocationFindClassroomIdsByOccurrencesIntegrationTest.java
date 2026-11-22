package ar.edu.utn.frc.siga.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("AllocationService.findClassroomIdsByOccurrences (integración)")
class AllocationFindClassroomIdsByOccurrencesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AllocationService allocationService;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private IntegrationTestData testData;

    private List<Occurrence> threeWeeklyOccurrences() {
        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 100);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(8, 0), 90, start, start.plusWeeks(2), 30);
        return occurrenceRepository.findByEvent_Id(eventId);
    }

    private void allocate(Occurrence occurrence, Classroom classroom) {
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(AllocationSource.MANUAL).build());
    }

    @Test
    @DisplayName("Devuelve el aula solo de las ocurrencias con asignación, y las sin asignar no aparecen")
    void returnsClassroomOnlyForAllocatedOccurrences() {
        List<Occurrence> occurrences = threeWeeklyOccurrences();
        Classroom first = testData.aula(testData.edificio());
        Classroom second = testData.aula(testData.edificio());
        allocate(occurrences.get(0), first);
        allocate(occurrences.get(1), second);

        Map<Long, Long> result = allocationService.findClassroomIdsByOccurrences(
                occurrences.stream().map(Occurrence::getId).toList());

        assertThat(occurrences).hasSize(3);
        assertThat(result).containsOnly(
                Map.entry(occurrences.get(0).getId(), first.getId()),
                Map.entry(occurrences.get(1).getId(), second.getId()));
    }

    @Test
    @DisplayName("No devuelve asignaciones de ocurrencias que no se pidieron")
    void ignoresOccurrencesNotRequested() {
        List<Occurrence> occurrences = threeWeeklyOccurrences();
        Classroom classroom = testData.aula(testData.edificio());
        allocate(occurrences.get(0), classroom);
        allocate(occurrences.get(1), classroom);

        Map<Long, Long> result = allocationService.findClassroomIdsByOccurrences(List.of(occurrences.get(1).getId()));

        assertThat(result).containsOnly(Map.entry(occurrences.get(1).getId(), classroom.getId()));
    }

    @Test
    @DisplayName("Un aula desactivada después de asignarse sigue apareciendo en el mapa")
    void includesDeactivatedClassroom() {
        List<Occurrence> occurrences = threeWeeklyOccurrences();
        Classroom classroom = testData.aula(testData.edificio());
        allocate(occurrences.get(0), classroom);
        classroom.deactivate();
        classroomRepository.save(classroom);

        Map<Long, Long> result = allocationService.findClassroomIdsByOccurrences(List.of(occurrences.get(0).getId()));

        assertThat(result).containsOnly(Map.entry(occurrences.get(0).getId(), classroom.getId()));
    }

    @Test
    @DisplayName("Sin ids devuelve un mapa vacío")
    void emptyIdsReturnEmptyMap() {
        assertThat(allocationService.findClassroomIdsByOccurrences(List.of())).isEmpty();
    }
}
