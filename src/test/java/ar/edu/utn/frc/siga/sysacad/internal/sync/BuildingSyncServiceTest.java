package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
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
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingSyncService (thin syncer)")
class BuildingSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private BuildingService buildingService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private BuildingSyncService service;

    @BeforeEach
    void setUp() {
        service = new BuildingSyncService(buildingService, syncStateService, new SysacadProperties());
    }

    @Test
    @DisplayName("view: expone la vista EDIFICIOS")
    void viewReturnsEdificios() {
        assertThat(service.view()).isEqualTo(SysacadView.EDIFICIOS);
    }

    @Test
    @DisplayName("sync: traduce cada fila de SysAcad a un comando y delega el upsert en BuildingService")
    void syncTranslatesRowsAndDelegatesToService() {
        when(catalogReader.findBuildings()).thenReturn(List.of(new SysacadBuildingDto(4, "Edif.Malvinas")));
        when(buildingService.syncBuildings(anyList(), eq(50))).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BuildingSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(buildingService).syncBuildings(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(new BuildingSyncCommand(4, "Edif.Malvinas"));
        verify(syncStateService).recordSuccess(SysacadView.EDIFICIOS, 1);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findBuildings()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(eq(SysacadView.EDIFICIOS), startsWith("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("sync: con la vista vacía no llama al servicio de dominio y registra el fallo")
    void syncWithEmptyViewSkipsDomainServiceAndRecordsFailure() {
        when(catalogReader.findBuildings()).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SysacadEmptyViewException.class);

        verify(buildingService, never()).syncBuildings(anyList(), eq(50));
        verify(syncStateService).recordFailure(eq(SysacadView.EDIFICIOS), anyString());
        verify(syncStateService, never()).recordSuccess(any(), anyInt());
    }
}
