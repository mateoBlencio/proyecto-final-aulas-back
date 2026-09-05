package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
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
@DisplayName("ClassroomSyncService (thin syncer)")
class ClassroomSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private ClassroomSyncService service;

    @BeforeEach
    void setUp() {
        service = new ClassroomSyncService(classroomService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista AULAS")
    void viewReturnsAulas() {
        assertThat(service.view()).isEqualTo(SysacadView.AULAS);
    }

    @Test
    @DisplayName("sync: traduce cada fila de SysAcad a un comando y delega el upsert en ClassroomService")
    void syncTranslatesRowsAndDelegatesToService() {
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, true, 70)));
        when(classroomService.syncClassrooms(anyList())).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassroomSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(classroomService).syncClassrooms(captor.capture());
        assertThat(captor.getValue()).containsExactly(new ClassroomSyncCommand(101, 2, true, 70));
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 1);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findClassrooms()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.AULAS, "SysAcad caído");
    }
}
