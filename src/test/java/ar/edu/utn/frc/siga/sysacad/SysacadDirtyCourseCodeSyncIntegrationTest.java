package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Feeds the real mapper, snapshot and sync services with dirty Sysacad rows (the same commission as
 * "5D1" and "5D1."); only the HTTP fetchers are mocked.
 */
@DisplayName("Sync de SysAcad con códigos de curso sucios (integración)")
@TestPropertySource(properties = "siga.sysacad.enabled=true")
class SysacadDirtyCourseCodeSyncIntegrationTest extends AbstractIntegrationTest {

    private static final int SPECIALTY = 9901;
    private static final int STUDY_PLAN = 9902;
    private static final int SUBJECT = 987654;
    private static final int YEAR = 2031;

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

    @Autowired
    private SysacadSyncOrchestrator orchestrator;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private SubjectCommissionRepository subjectCommissionRepository;
    @Autowired
    private RecurringEventRepository recurringEventRepository;

    private static RawSchedule scheduleRow(String course) {
        return new RawSchedule(course, 10, 805, 15, "Edif. Test",
                2, 1, "1", "1",
                "10:30", "12:45", "10:30-12:45", 135,
                SPECIALTY, "Especialidad Test", STUDY_PLAN, SUBJECT, "Materia Test", 30);
    }

    @Test
    @DisplayName("la misma comisión como '5D1' y '5D1.' termina en una comisión, un vínculo y un evento")
    void sameCommissionWithDirtyCodeIsNotDuplicated() {
        Mockito.when(subjectFetcher.fetch())
                .thenReturn(List.of(new RawSubject(SPECIALTY, STUDY_PLAN, SUBJECT, "Materia Test")));
        Mockito.when(commissionFetcher.fetch()).thenReturn(List.of(
                new RawCommission("5D1", SPECIALTY, STUDY_PLAN, SUBJECT, YEAR, 10),
                new RawCommission("5D1.", SPECIALTY, STUDY_PLAN, SUBJECT, YEAR, 10)));
        Mockito.when(scheduleFetcher.fetch()).thenReturn(List.of(scheduleRow("5D1"), scheduleRow("5D1.")));

        orchestrator.sync(SysacadView.MATERIAS);
        orchestrator.sync(SysacadView.COMISIONES);
        orchestrator.sync(SysacadView.EVENTOS);

        List<Commission> commissions = commissionRepository.findAll().stream()
                .filter(c -> c.getCourseCode().startsWith("5D1"))
                .toList();
        assertThat(commissions).extracting(Commission::getCourseCode).containsExactly("5D1");

        Subject subject = subjectRepository.findByStudyPlan_Specialty_SpecialtyCode(SPECIALTY).stream()
                .filter(s -> s.getCode() == SUBJECT)
                .findFirst().orElseThrow();
        List<SubjectCommission> links = subjectCommissionRepository.findBySubject_Id(subject.getId());
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getCommission().getId()).isEqualTo(commissions.get(0).getId());

        List<RecurringEvent> events = recurringEventRepository.findBySubjectIdInAndCommissionIdIn(
                List.of(subject.getId()), List.of(commissions.get(0).getId()));
        assertThat(events).hasSize(1);
    }
}
