package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private SpecialtyRepository specialtyRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private SysacadSyncStateService syncStateService;

    private CommissionSyncService service() {
        return new CommissionSyncService(catalogReader, commissionRepository, academicPeriodRepository,
                specialtyRepository, studyPlanRepository, syncStateService);
    }

    @Test
    @DisplayName("sync: resuelve el AcademicPeriod anual a partir del año de la comisión y da de alta la comisión")
    void syncCreatesCommissionUnderAnnualPeriod() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        Specialty specialty = Specialty.builder().id(1L).specialtyCode(1).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(specialtyRepository.findBySpecialtyCode(1)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialty(2024, specialty)).thenReturn(Optional.empty());
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
    @DisplayName("sync: crea el StudyPlan cuando no existe para la especialidad+plan de la fila")
    void syncCreatesMissingStudyPlan() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
        Specialty specialty = Specialty.builder().id(1L).specialtyCode(1).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(specialtyRepository.findBySpecialtyCode(1)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialty(2024, specialty)).thenReturn(Optional.empty());
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(studyPlanRepository).save(argThat((StudyPlan plan) ->
                plan.getPlanCode().equals(2024) && plan.getSpecialty().equals(specialty)));
    }

    @Test
    @DisplayName("sync: si la especialidad de la fila no está sincronizada, no crea el StudyPlan pero igual sincroniza la comisión")
    void syncSkipsStudyPlanWhenSpecialtyUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        AcademicPeriod period = AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();

        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(commissionRepository.findAll()).thenReturn(List.of());
        when(specialtyRepository.findBySpecialtyCode(1)).thenReturn(Optional.empty());
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(period));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        verify(studyPlanRepository, never()).save(any());
        verify(commissionRepository).save(any(Commission.class));
    }
}
