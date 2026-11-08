package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.HolidayViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("Sync de SysAcad: duración del evento por HoraComienzo/HoraFin (integración)")
@TestPropertySource(properties = {
        "siga.sysacad.enabled=true",
        "siga.sysacad.sync.min-valid-rows-percent=0"})
class SysacadEventDurationSyncIntegrationTest extends AbstractIntegrationTest {

    private static final int SPECIALTY = 9911;
    private static final int STUDY_PLAN = 9912;
    private static final int SUBJECT = 987655;
    private static final int YEAR = 2032;
    private static final String COURSE = "7Z1";

    @MockitoBean
    private BuildingViewFetcher buildingFetcher;
    @MockitoBean
    private ClassroomViewFetcher classroomFetcher;
    @MockitoBean
    private SpecialtyViewFetcher specialtyFetcher;
    @MockitoBean
    private SubjectViewFetcher subjectFetcher;
    @MockitoBean
    private CommissionViewFetcher commissionFetcher;
    @MockitoBean
    private ScheduleViewFetcher scheduleFetcher;
    @MockitoBean
    private HolidayViewFetcher holidayFetcher;

    @Autowired
    private SysacadSyncOrchestrator orchestrator;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private RecurringEventRepository recurringEventRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static RawSchedule scheduleRow(String horaFin, Integer duracion) {
        return new RawSchedule(COURSE, 10, 805, 15, "Edif. Test",
                2, 1, "1", "1",
                "10:30", horaFin, "10:30-12:45", duracion,
                SPECIALTY, "Especialidad Test", STUDY_PLAN, SUBJECT, "Materia Test", 30);
    }

    private List<RecurringEvent> eventsOfCourse() {
        Subject subject = subjectRepository.findByStudyPlan_Specialty_SpecialtyCode(SPECIALTY).stream()
                .filter(s -> s.getCode() == SUBJECT)
                .findFirst().orElseThrow();
        Commission commission = commissionRepository.findAll().stream()
                .filter(c -> COURSE.equals(c.getCourseCode()))
                .findFirst().orElseThrow();
        return recurringEventRepository.findBySubjectIdInAndCommissionIdIn(
                List.of(subject.getId()), List.of(commission.getId()));
    }

    private List<Long> occurrenceIds(Long eventId) {
        return jdbc.queryForList(
                "select id_ocurrencia from ocurrencia where id_evento_academico = ? order by id_ocurrencia",
                Long.class, eventId);
    }

    @Test
    @DisplayName("un evento con 120 min sin editar pasa a 135 cuando la vista trae 10:30-12:45, sin duplicar el evento ni las ocurrencias")
    void eventWithColumnDurationIsCorrectedToRangeDuration() {
        Mockito.when(subjectFetcher.fetch())
                .thenReturn(List.of(new RawSubject(SPECIALTY, STUDY_PLAN, SUBJECT, "Materia Test")));
        Mockito.when(commissionFetcher.fetch())
                .thenReturn(List.of(new RawCommission(COURSE, SPECIALTY, STUDY_PLAN, SUBJECT, YEAR, 10)));
        // Sin HoraFin el mapper usa DURACION: deja el estado que hoy tiene dev (120 min, hash de 120).
        Mockito.when(scheduleFetcher.fetch()).thenReturn(List.of(scheduleRow(null, 120)));
        orchestrator.resyncAll();

        List<RecurringEvent> seeded = eventsOfCourse();
        assertThat(seeded).hasSize(1);
        Long eventId = seeded.getFirst().getId();
        assertThat(seeded.getFirst().getDuration().toMinutes()).isEqualTo(120);
        assertThat(seeded.getFirst().getSysacadHash()).isEqualTo(Hashes.sha256Hex(120));
        List<Long> occurrencesBefore = occurrenceIds(eventId);
        assertThat(occurrencesBefore).isNotEmpty();

        Mockito.when(scheduleFetcher.fetch()).thenReturn(List.of(scheduleRow("12:45", 120)));
        orchestrator.resyncAll();

        List<RecurringEvent> after = eventsOfCourse();
        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getId()).isEqualTo(eventId);
        assertThat(after.getFirst().getDuration().toMinutes()).isEqualTo(135);
        assertThat(after.getFirst().getSysacadHash()).isEqualTo(Hashes.sha256Hex(135));
        assertThat(occurrenceIds(eventId)).isEqualTo(occurrencesBefore);
    }
}
