package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionSyncService")
class CommissionSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;

    @Mock
    private CommissionRepository commissionRepository;

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectCommissionRepository subjectCommissionRepository;

    @Mock
    private StudyPlanResolver studyPlanResolver;

    @Mock
    private SysacadSyncStateService syncStateService;

    private CommissionSyncService service() {
        return new CommissionSyncService(catalogReader, commissionRepository, academicPeriodRepository,
                subjectRepository, subjectCommissionRepository, studyPlanResolver, syncStateService);
    }

    private static StudyPlan studyPlan() {
        return StudyPlan.builder().id(1L).planCode(2024)
                .specialty(Specialty.builder().id(1L).specialtyCode(1).build()).build();
    }

    @Test
    @DisplayName("sync: resuelve el AcademicPeriod anual a partir del año de la comisión y da de alta la comisión")
    void syncCreatesCommissionUnderAnnualPeriod() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        StudyPlan studyPlan = studyPlan();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(syncStateService).recordSuccess(SysacadView.COMISIONES, 1);
        verify(academicPeriodRepository).findByYearAndSemester(2026, TermType.ANUAL.getSemester());
        verify(commissionRepository).save(argThat((Commission commission) ->
                Boolean.TRUE.equals(commission.getSysacadEnabled())
                        && "101".equals(commission.getCourseCode())
                        && period.equals(commission.getAcademicPeriod())));
    }

    @Test
    @DisplayName("sync: delega en StudyPlanResolver la especialidad+plan de la fila")
    void syncDelegatesStudyPlanResolution() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.empty());
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(studyPlanResolver).findOrCreate(eq(1), eq(2024), any());
    }

    @Test
    @DisplayName("sync: si StudyPlanResolver no resuelve la especialidad, igual sincroniza la comisión")
    void syncSkipsStudyPlanWhenUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.empty());
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(commissionRepository).save(any(Commission.class));
        verify(syncStateService, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("sync: crea materia_comision cuando la materia y los inscriptos están disponibles")
    void syncCreatesSubjectCommissionLink() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions())
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 55, 30)));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectCommissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        ArgumentCaptor<SubjectCommission> saved = ArgumentCaptor.forClass(SubjectCommission.class);
        verify(subjectCommissionRepository).save(saved.capture());
        assertThat(saved.getValue().getSubject()).isEqualTo(subject);
        assertThat(saved.getValue().getEnrolledCount()).isEqualTo(30);
        verify(syncStateService).recordSuccess(SysacadView.COMISIONES, 2);
    }

    @Test
    @DisplayName("sync: actualiza cantidad_inscriptos cuando cambió respecto al valor existente")
    void syncUpdatesEnrolledCountWhenChanged() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();
        Commission commission = Commission.builder().id(9L).courseCode("101").academicPeriod(period)
                .sysacadHash(Hashes.sha256Hex("101", 9L, 1, 2024, 55)).sysacadEnabled(true).build();
        SubjectCommission existingLink = SubjectCommission.builder()
                .id(new SubjectCommissionId(3L, 9L))
                .subject(subject).commission(commission).enrolledCount(20).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions())
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 55, 30)));
        when(commissionRepository.findAll()).thenReturn(List.of(commission));
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectCommissionRepository.findAll()).thenReturn(List.of(existingLink));
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));

        service().sync();

        assertThat(existingLink.getEnrolledCount()).isEqualTo(30);
        verify(subjectCommissionRepository).save(existingLink);
    }

    @Test
    @DisplayName("sync: si la materia de la fila no está sincronizada, no crea el link pero igual sincroniza la comisión")
    void syncSkipsLinkWhenSubjectUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        StudyPlan studyPlan = studyPlan();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions())
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 55, 30)));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(subjectCommissionRepository, never()).save(any());
        verify(commissionRepository).save(any(Commission.class));
    }

    @Test
    @DisplayName("sync: si no hay inscriptos para curso+materia, no crea el link")
    void syncSkipsLinkWhenEnrollmentUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of());
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(subjectCommissionRepository, never()).save(any());
    }
}
