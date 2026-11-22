package ar.edu.utn.frc.siga.testsupport;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
public abstract class AbstractDeactivationIntegrationTest extends AbstractIntegrationTest {

    protected static final String CLASSROOM_NOTE = "Aula deshabilitada";
    protected static final String BUILDING_NOTE = "Edificio deshabilitado";

    @Autowired
    protected IntegrationTestData testData;
    @Autowired
    protected OccurrenceRepository occurrenceRepository;
    @Autowired
    protected AllocationRepository allocationRepository;
    @Autowired
    protected OccurrenceService occurrenceService;
    @Autowired
    protected JdbcTemplate jdbc;

    protected static LocalDate futureMonday() {
        return LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** El alta valida la ventana horaria: un inicio anterior a las 08:00 se crea a las 08:00 y se retrasa en base. */
    protected Long createOccurrence(LocalDate date, LocalTime start) {
        var sc = testData.materiaYComision();
        LocalTime creationStart = start.isBefore(LocalTime.of(8, 0)) ? LocalTime.of(8, 0) : start;
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(),
                creationStart, 90, date, date, 30);
        if (!creationStart.equals(start)) {
            jdbc.update("update evento_academico set hora_inicio = ? where id_evento_academico = ?",
                    java.sql.Time.valueOf(start), eventId);
        }
        return occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
    }

    protected Long allocatedOccurrence(Classroom classroom, LocalDate date) throws Exception {
        Long occurrenceId = createOccurrence(date, LocalTime.of(18, 0));
        allocate(occurrenceId, classroom).andExpect(status().isCreated());
        return occurrenceId;
    }

    protected Long mirrorAllocatedIn(Long principalId, Classroom classroom) throws Exception {
        Long mirrorId = occurrenceService.createSimultaneous(principalId, 1).getFirst();
        allocate(mirrorId, classroom).andExpect(status().isCreated());
        return mirrorId;
    }

    protected Long allocateDirectly(Long occurrenceId, Classroom classroom) {
        return allocationRepository.save(Allocation.builder().occurrenceId(occurrenceId)
                .classroomId(classroom.getId()).source(AllocationSource.MANUAL).build()).getId();
    }

    protected ResultActions allocate(Long occurrenceId, Classroom classroom) throws Exception {
        return mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceId + "],\"classroomId\":"
                        + classroom.getId() + "}]}"));
    }

    protected ResultActions deactivateClassroom(Classroom classroom) throws Exception {
        return mockMvc.perform(delete("/v1/classrooms/{id}/activation", classroom.getId()));
    }

    protected ResultActions classroomImpact(Classroom classroom) throws Exception {
        return mockMvc.perform(get("/v1/classrooms/{id}/deactivation-impact", classroom.getId()));
    }

    protected Occurrence occurrence(Long id) {
        return occurrenceRepository.findById(id).orElseThrow();
    }

    protected Optional<Allocation> allocationOf(Long occurrenceId) {
        return allocationRepository.findByOccurrenceId(occurrenceId);
    }

    protected void assertReleased(Long occurrenceId, ReleaseReason reason, String note, Classroom previous) {
        Occurrence occurrence = occurrence(occurrenceId);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(occurrence.getReleaseReason()).isEqualTo(reason);
        assertThat(occurrence.getReleaseNote()).isEqualTo(note);
        assertThat(occurrence.getPreviousClassroomId()).isEqualTo(previous.getId());
        assertThat(allocationOf(occurrenceId)).isEmpty();
    }

    protected void assertAllocatedIn(Long occurrenceId, Classroom classroom) {
        assertThat(occurrence(occurrenceId).getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(allocationOf(occurrenceId)).hasValueSatisfying(
                a -> assertThat(a.getClassroomId()).isEqualTo(classroom.getId()));
    }
}
