package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
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
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionServiceImpl")
class CommissionServiceImplTest {

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
    private CommissionMapper commissionMapper;

    private CommissionServiceImpl service;

    private final AcademicPeriod period = AcademicPeriod.builder().id(1L).year(2026).semester(1).build();

    @BeforeEach
    void setUp() {
        service = new CommissionServiceImpl(commissionRepository, academicPeriodRepository, subjectRepository,
                subjectCommissionRepository, studyPlanResolver, commissionMapper);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando la comisión existe")
    void findByIdReturnsMappedDto() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", null);
        when(commissionRepository.findActiveById(3L)).thenReturn(Optional.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        CommissionResponseDto result = service.findById(3L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si la comisión no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingCommissionThrowsResourceNotFound() {
        when(commissionRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Commission not found with id: 99");
    }

    @Test
    @DisplayName("findByIds: mapea cada comisión encontrada")
    void findByIdsMapsAllFound() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", null);
        when(commissionRepository.findAllById(List.of(3L))).thenReturn(List.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        List<CommissionResponseDto> result = service.findByIds(List.of(3L));

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("find: si la comisión existe para ese período, devuelve el DTO mapeado")
    void findWithExistingCommissionReturnsMappedDto() {
        Commission existing = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", null);
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndAcademicPeriod("K1001", period))
                .thenReturn(Optional.of(existing));
        when(commissionMapper.toDto(existing)).thenReturn(dto);

        CommissionResponseDto result = service.findByCourseAndPeriod("K1001", 2026, 1);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("find: si no existe la comisión para ese período, lanza ResourceNotFoundException")
    void findWithoutExistingCommissionThrowsResourceNotFound() {
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndAcademicPeriod("K1001", period))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCourseAndPeriod("K1001", 2026, 1))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("find: si el período académico no existe, lanza ResourceNotFoundException con la clave year-semester")
    void findWithMissingPeriodThrowsResourceNotFound() {
        when(academicPeriodRepository.findByYearAndSemester(2030, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCourseAndPeriod("K1001", 2030, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("AcademicPeriod not found with id: 2030-2");
        verify(commissionRepository, never()).findByCourseCodeAndAcademicPeriod(any(), any());
    }

    @Test
    @DisplayName("findAll: mapea todas las comisiones del repositorio")
    void findAllMapsAllCommissions() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", null);
        when(commissionRepository.findAllActive()).thenReturn(List.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        List<CommissionResponseDto> result = service.findAll();

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("findActiveByCourseCode: una sola comisión vigente para el curso → devuelve el DTO mapeado")
    void findActiveByCourseCodeWithSingleActiveReturnsMappedDto() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period)
                .sysacadEnabled(true).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", null);
        when(commissionRepository.findByCourseCodeAndSysacadEnabledTrueAndDeletedAtIsNull("K1001")).thenReturn(List.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        CommissionResponseDto result = service.findActiveByCourseCode("K1001");

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findActiveByCourseCode: sin comisiones vigentes para el curso, lanza ResourceNotFoundException")
    void findActiveByCourseCodeWithoutActiveThrowsResourceNotFound() {
        when(commissionRepository.findByCourseCodeAndSysacadEnabledTrueAndDeletedAtIsNull("K1001")).thenReturn(List.of());

        assertThatThrownBy(() -> service.findActiveByCourseCode("K1001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findActiveByCourseCode: más de una comisión vigente para el curso (ambigua), lanza ResourceNotFoundException sin mapear")
    void findActiveByCourseCodeWithMultipleActiveThrowsResourceNotFound() {
        Commission commission1 = Commission.builder().id(3L).courseCode("K1001").academicPeriod(period)
                .sysacadEnabled(true).build();
        Commission commission2 = Commission.builder().id(4L).courseCode("K1001").academicPeriod(period)
                .sysacadEnabled(true).build();
        when(commissionRepository.findByCourseCodeAndSysacadEnabledTrueAndDeletedAtIsNull("K1001"))
                .thenReturn(List.of(commission1, commission2));

        assertThatThrownBy(() -> service.findActiveByCourseCode("K1001"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commissionMapper, never()).toDto(any());
    }

    private static StudyPlan studyPlan() {
        return StudyPlan.builder().id(1L).planCode(2024)
                .specialty(Specialty.builder().id(1L).specialtyCode(1).build()).build();
    }

    private static AcademicPeriod annualPeriod() {
        return AcademicPeriod.builder().id(9L).year(2026).semester(TermType.ANUAL.getSemester()).build();
    }

    @Test
    @DisplayName("syncCommissions: resuelve el AcademicPeriod anual a partir del año de la comisión y da de alta la comisión")
    void syncCommissionsCreatesCommissionUnderAnnualPeriod() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, null);
        AcademicPeriod annualPeriod = annualPeriod();
        StudyPlan studyPlan = studyPlan();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncCommissions(List.of(command));

        assertThat(affected).isEqualTo(1);
        verify(academicPeriodRepository).findByYearAndSemester(2026, TermType.ANUAL.getSemester());
        verify(commissionRepository).save(argThat((Commission commission) ->
                Boolean.TRUE.equals(commission.getSysacadEnabled())
                        && "101".equals(commission.getCourseCode())
                        && annualPeriod.equals(commission.getAcademicPeriod())));
    }

    @Test
    @DisplayName("syncCommissions: delega en StudyPlanResolver la especialidad+plan del comando")
    void syncCommissionsDelegatesStudyPlanResolution() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, null);
        AcademicPeriod annualPeriod = annualPeriod();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.empty());
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncCommissions(List.of(command));

        verify(studyPlanResolver).findOrCreate(eq(1), eq(2024), any());
    }

    @Test
    @DisplayName("syncCommissions: si StudyPlanResolver no resuelve la especialidad, igual sincroniza la comisión")
    void syncCommissionsSkipsStudyPlanWhenUnresolved() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, null);
        AcademicPeriod annualPeriod = annualPeriod();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.empty());
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncCommissions(List.of(command));

        verify(commissionRepository).save(any(Commission.class));
    }

    @Test
    @DisplayName("syncCommissions: crea materia_comision cuando la materia y los inscriptos están disponibles")
    void syncCommissionsCreatesSubjectCommissionLink() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30);
        AcademicPeriod annualPeriod = annualPeriod();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectCommissionRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncCommissions(List.of(command));

        ArgumentCaptor<SubjectCommission> saved = ArgumentCaptor.forClass(SubjectCommission.class);
        verify(subjectCommissionRepository).save(saved.capture());
        assertThat(saved.getValue().getSubject()).isEqualTo(subject);
        assertThat(saved.getValue().getEnrolledCount()).isEqualTo(30);
        assertThat(affected).isEqualTo(2);
    }

    @Test
    @DisplayName("syncCommissions: actualiza cantidad_inscriptos cuando cambió respecto al valor existente")
    void syncCommissionsUpdatesEnrolledCountWhenChanged() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30);
        AcademicPeriod annualPeriod = annualPeriod();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();
        Commission commission = Commission.builder().id(9L).courseCode("101").academicPeriod(annualPeriod)
                .sysacadHash(Hashes.sha256Hex("101", 9L, 1, 2024, 55)).sysacadEnabled(true).build();
        SubjectCommission existingLink = SubjectCommission.builder()
                .id(new SubjectCommissionId(3L, 9L))
                .subject(subject).commission(commission).enrolledCount(20).build();

        when(commissionRepository.findAll()).thenReturn(List.of(commission));
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(subjectCommissionRepository.findAll()).thenReturn(List.of(existingLink));
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));

        service.syncCommissions(List.of(command));

        assertThat(existingLink.getEnrolledCount()).isEqualTo(30);
        verify(subjectCommissionRepository).save(existingLink);
    }

    @Test
    @DisplayName("syncCommissions: si la materia del comando no está sincronizada, no crea el link pero igual sincroniza la comisión")
    void syncCommissionsSkipsLinkWhenSubjectUnresolved() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30);
        AcademicPeriod annualPeriod = annualPeriod();
        StudyPlan studyPlan = studyPlan();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of());
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncCommissions(List.of(command));

        verify(subjectCommissionRepository, never()).save(any());
        verify(commissionRepository).save(any(Commission.class));
    }

    @Test
    @DisplayName("syncCommissions: si el comando no trae inscriptos para curso+materia, no crea el link")
    void syncCommissionsSkipsLinkWhenEnrollmentUnresolved() {
        CommissionSyncCommand command = new CommissionSyncCommand("101", 1, 2024, 55, 2026, null);
        AcademicPeriod annualPeriod = annualPeriod();
        StudyPlan studyPlan = studyPlan();
        Subject subject = Subject.builder().id(3L).code(55).studyPlan(studyPlan).build();

        when(commissionRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(studyPlanResolver.findOrCreate(eq(1), eq(2024), any())).thenReturn(Optional.of(studyPlan));
        when(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester()))
                .thenReturn(Optional.of(annualPeriod));
        when(commissionRepository.save(any(Commission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncCommissions(List.of(command));

        verify(subjectCommissionRepository, never()).save(any());
    }
}
