package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectSyncService")
class SubjectSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private StudyPlanResolver studyPlanResolver;

    @Mock
    private SysacadSyncStateService syncStateService;

    private SubjectSyncService service() {
        return new SubjectSyncService(catalogReader, subjectRepository, studyPlanResolver, syncStateService);
    }

    private static StudyPlan studyPlan() {
        return StudyPlan.builder().id(1L).planCode(94)
                .specialty(Specialty.builder().id(1L).specialtyCode(17).build()).build();
    }

    @Test
    @DisplayName("sync: inserta la materia que no existe con columnas de control")
    void syncInsertsUnknownSubject() {
        StudyPlan studyPlan = studyPlan();
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "C")));
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(studyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().sync();

        ArgumentCaptor<Subject> saved = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(saved.capture());
        Subject inserted = saved.getValue();
        assertThat(inserted.getCode()).isEqualTo(519);
        assertThat(inserted.getName()).isEqualTo("Análisis Matemático I");
        assertThat(inserted.getTerm()).isEqualTo("C");
        assertThat(inserted.getStudyPlan()).isEqualTo(studyPlan);
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Análisis Matemático I", "C"));
        verify(syncStateService).recordSuccess(SysacadView.MATERIAS, 1);
    }

    @Test
    @DisplayName("sync: actualiza el nombre y el dictado de la materia existente cuando cambiaron upstream")
    void syncUpdatesRenamedSubject() {
        StudyPlan studyPlan = studyPlan();
        Subject existing = Subject.builder().id(1L).code(519).name("Analisis I").term("A").studyPlan(studyPlan)
                .sysacadHash(Hashes.sha256Hex("Analisis I", "A")).build();
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "C")));
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(studyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of(existing));

        service().sync();

        assertThat(existing.getName()).isEqualTo("Análisis Matemático I");
        assertThat(existing.getTerm()).isEqualTo("C");
        verify(subjectRepository).save(existing);
    }

    @Test
    @DisplayName("sync: no escribe cuando el hash no cambió")
    void syncSkipsUnchangedSubject() {
        StudyPlan studyPlan = studyPlan();
        Subject existing = Subject.builder().id(1L).code(519).name("Análisis Matemático I").term("C").studyPlan(studyPlan)
                .sysacadHash(Hashes.sha256Hex("Análisis Matemático I", "C")).build();
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "C")));
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.of(studyPlan));
        when(subjectRepository.findAll()).thenReturn(List.of(existing));

        service().sync();

        verify(subjectRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.MATERIAS, 0);
    }

    @Test
    @DisplayName("sync: fila con datos incompletos se ignora")
    void syncIgnoresIncompleteRow() {
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, null, 519, "Análisis Matemático I", "C")));
        when(subjectRepository.findAll()).thenReturn(List.of());

        service().sync();

        verify(subjectRepository, never()).save(any());
        verify(studyPlanResolver, never()).findOrCreate(any(), any(), any());
        verify(syncStateService).recordSuccess(SysacadView.MATERIAS, 0);
    }

    @Test
    @DisplayName("sync: si no se resuelve el plan de estudio, la materia se ignora")
    void syncSkipsWhenStudyPlanUnresolved() {
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "C")));
        when(studyPlanResolver.findOrCreate(eq(17), eq(94), any())).thenReturn(Optional.empty());
        when(subjectRepository.findAll()).thenReturn(List.of());

        service().sync();

        verify(subjectRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.MATERIAS, 0);
    }
}
