package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.command.SubjectSyncCommand;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectServiceImpl")
class SubjectServiceImplTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private StudyPlanRepository studyPlanRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private StudyPlanResolver studyPlanResolver;

    private SubjectServiceImpl service;

    private final Specialty specialty = Specialty.builder().id(1L).specialtyCode(10).name("Informática").build();
    private final StudyPlan studyPlan = StudyPlan.builder().id(2L).planCode(2020).specialty(specialty).build();

    @BeforeEach
    void setUp() {
        service = new SubjectServiceImpl(
                subjectRepository, studyPlanRepository, specialtyRepository, subjectMapper, studyPlanResolver);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando la materia existe")
    void findByIdReturnsMappedDto() {
        Subject subject = Subject.builder().id(5L).code(101).name("Algoritmos").studyPlan(studyPlan).build();
        SubjectResponseDto dto = new SubjectResponseDto(5L, 101, "Algoritmos", null, null);
        when(subjectRepository.findActiveById(5L)).thenReturn(Optional.of(subject));
        when(subjectMapper.toDto(subject)).thenReturn(dto);

        SubjectResponseDto result = service.findById(5L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si la materia no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingSubjectThrowsResourceNotFound() {
        when(subjectRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Subject not found with id: 99");
    }

    @Test
    @DisplayName("findByIds: mapea cada materia encontrada, sin fallar por ids inexistentes")
    void findByIdsMapsAllFound() {
        Subject subject = Subject.builder().id(5L).code(101).name("Algoritmos").studyPlan(studyPlan).build();
        SubjectResponseDto dto = new SubjectResponseDto(5L, 101, "Algoritmos", null, null);
        when(subjectRepository.findAllById(List.of(5L, 99L))).thenReturn(List.of(subject));
        when(subjectMapper.toDto(subject)).thenReturn(dto);

        List<SubjectResponseDto> result = service.findByIds(List.of(5L, 99L));

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: mapea todas las materias del repositorio")
    void findAllMapsAllSubjects() {
        Subject subject = Subject.builder().id(5L).code(101).name("Algoritmos").studyPlan(studyPlan).build();
        SubjectResponseDto dto = new SubjectResponseDto(5L, 101, "Algoritmos", null, null);
        when(subjectRepository.findAllActive()).thenReturn(List.of(subject));
        when(subjectMapper.toDto(subject)).thenReturn(dto);

        assertThat(service.findAll()).containsExactly(dto);
    }

    @Test
    @DisplayName("findBySpecialtyCode: mapea las materias de todos los planes de esa especialidad")
    void findBySpecialtyCodeMapsAllSubjects() {
        Subject subject = Subject.builder().id(5L).code(101).name("Algoritmos").studyPlan(studyPlan).build();
        SubjectResponseDto dto = new SubjectResponseDto(5L, 101, "Algoritmos", null, null);
        when(subjectRepository.findByStudyPlan_Specialty_SpecialtyCodeAndDeletedAtIsNull(10)).thenReturn(List.of(subject));
        when(subjectMapper.toDto(subject)).thenReturn(dto);

        assertThat(service.findBySpecialtyCode(10)).containsExactly(dto);
    }

    @Test
    @DisplayName("findBySpecialtyCode: sin materias vinculadas, devuelve lista vacía (no lanza)")
    void findBySpecialtyCodeWithoutMatchesReturnsEmptyList() {
        when(subjectRepository.findByStudyPlan_Specialty_SpecialtyCodeAndDeletedAtIsNull(999)).thenReturn(List.of());

        assertThat(service.findBySpecialtyCode(999)).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndStudyPlan: devuelve el DTO mapeado cuando la materia existe para ese plan")
    void findByCodeAndStudyPlanReturnsMappedDto() {
        Subject existing = Subject.builder().id(5L).code(101).name("Algoritmos").studyPlan(studyPlan).build();
        SubjectResponseDto dto = new SubjectResponseDto(5L, 101, "Algoritmos", "Anual", null);
        when(specialtyRepository.findBySpecialtyCode(10)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedAtIsNull(2020, specialty)).thenReturn(Optional.of(studyPlan));
        when(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(101, studyPlan)).thenReturn(Optional.of(existing));
        when(subjectMapper.toDto(existing)).thenReturn(dto);

        SubjectResponseDto result = service.findByCodeAndStudyPlan(101, 2020, 10);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByCodeAndStudyPlan: si la especialidad no existe, lanza ResourceNotFoundException y no consulta el plan")
    void findByCodeAndStudyPlanWithMissingSpecialtyThrowsResourceNotFound() {
        when(specialtyRepository.findBySpecialtyCode(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCodeAndStudyPlan(101, 2020, 999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Specialty not found with id: 999");
    }

    @Test
    @DisplayName("findByCodeAndStudyPlan: si el plan de estudio no existe para esa especialidad, lanza ResourceNotFoundException")
    void findByCodeAndStudyPlanWithMissingStudyPlanThrowsResourceNotFound() {
        when(specialtyRepository.findBySpecialtyCode(10)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedAtIsNull(2020, specialty)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCodeAndStudyPlan(101, 2020, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("StudyPlan not found with id: 2020");
    }

    @Test
    @DisplayName("findByCodeAndStudyPlan: si la materia no existe para ese plan, lanza ResourceNotFoundException")
    void findByCodeAndStudyPlanWithMissingSubjectThrowsResourceNotFound() {
        when(specialtyRepository.findBySpecialtyCode(10)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedAtIsNull(2020, specialty)).thenReturn(Optional.of(studyPlan));
        when(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(101, studyPlan)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCodeAndStudyPlan(101, 2020, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Subject not found with id: 101");
    }

    @Test
    @DisplayName("syncSubjects: inserta la materia que no existe con columnas de control")
    void syncSubjectsInsertsUnknownSubject() {
        StudyPlan syncStudyPlan = syncStudyPlan();
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(syncStudyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncSubjects(
                List.of(new SubjectSyncCommand(17, 94, 519, "Análisis Matemático I", "C")));

        ArgumentCaptor<Subject> saved = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(saved.capture());
        Subject inserted = saved.getValue();
        assertThat(inserted.getCode()).isEqualTo(519);
        assertThat(inserted.getName()).isEqualTo("Análisis Matemático I");
        assertThat(inserted.getTerm()).isEqualTo("C");
        assertThat(inserted.getStudyPlan()).isEqualTo(syncStudyPlan);
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Análisis Matemático I", "C"));
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncSubjects: actualiza el nombre y el dictado de la materia existente cuando cambiaron upstream")
    void syncSubjectsUpdatesRenamedSubject() {
        StudyPlan syncStudyPlan = syncStudyPlan();
        Subject existing = Subject.builder().id(1L).code(519).name("Analisis I").term("A").studyPlan(syncStudyPlan)
                .sysacadHash(Hashes.sha256Hex("Analisis I", "A")).build();
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(syncStudyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of(existing));

        service.syncSubjects(List.of(new SubjectSyncCommand(17, 94, 519, "Análisis Matemático I", "C")));

        assertThat(existing.getName()).isEqualTo("Análisis Matemático I");
        assertThat(existing.getTerm()).isEqualTo("C");
        verify(subjectRepository).save(existing);
    }

    @Test
    @DisplayName("syncSubjects: no escribe cuando el hash no cambió")
    void syncSubjectsSkipsUnchangedSubject() {
        StudyPlan syncStudyPlan = syncStudyPlan();
        Subject existing = Subject.builder().id(1L).code(519).name("Análisis Matemático I").term("C")
                .studyPlan(syncStudyPlan).sysacadHash(Hashes.sha256Hex("Análisis Matemático I", "C")).build();
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(syncStudyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of(existing));

        int affected = service.syncSubjects(
                List.of(new SubjectSyncCommand(17, 94, 519, "Análisis Matemático I", "C")));

        verify(subjectRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncSubjects: comando con datos incompletos se ignora")
    void syncSubjectsIgnoresIncompleteCommand() {
        when(subjectRepository.findAll()).thenReturn(List.of());

        int affected = service.syncSubjects(
                List.of(new SubjectSyncCommand(17, null, 519, "Análisis Matemático I", "C")));

        verify(subjectRepository, never()).save(any());
        verify(studyPlanResolver, never()).findOrCreate(any(), any(), any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncSubjects: si no se resuelve el plan de estudio, la materia se ignora")
    void syncSubjectsSkipsWhenStudyPlanUnresolved() {
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.empty());
        when(subjectRepository.findAll()).thenReturn(List.of());

        int affected = service.syncSubjects(
                List.of(new SubjectSyncCommand(17, 94, 519, "Análisis Matemático I", "C")));

        verify(subjectRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    private static StudyPlan syncStudyPlan() {
        return StudyPlan.builder().id(1L).planCode(94)
                .specialty(Specialty.builder().id(1L).specialtyCode(17).build()).build();
    }
}
