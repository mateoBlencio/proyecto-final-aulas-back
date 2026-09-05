package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.academic.service.command.SubjectSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectSyncService (thin syncer)")
class SubjectSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private SubjectService subjectService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private SubjectSyncService service;

    @BeforeEach
    void setUp() {
        service = new SubjectSyncService(catalogReader, subjectService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista MATERIAS")
    void viewReturnsMaterias() {
        assertThat(service.view()).isEqualTo(SysacadView.MATERIAS);
    }

    @Test
    @DisplayName("sync: traduce cada fila de SysAcad a un comando y delega el upsert en SubjectService")
    void syncTranslatesRowsAndDelegatesToService() {
        when(catalogReader.findSubjects())
                .thenReturn(List.of(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "C")));
        when(subjectService.syncSubjects(anyList())).thenReturn(1);

        service.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubjectSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(subjectService).syncSubjects(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new SubjectSyncCommand(17, 94, 519, "Análisis Matemático I", "C"));
        verify(syncStateService).recordSuccess(SysacadView.MATERIAS, 1);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findSubjects()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.MATERIAS, "SysAcad caído");
    }
}
