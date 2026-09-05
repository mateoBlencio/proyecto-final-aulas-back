package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
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
@DisplayName("SpecialtySyncService (thin syncer)")
class SpecialtySyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private SpecialtyService specialtyService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private SpecialtySyncService service;

    @BeforeEach
    void setUp() {
        service = new SpecialtySyncService(specialtyService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista ESPECIALIDADES")
    void viewReturnsEspecialidades() {
        assertThat(service.view()).isEqualTo(SysacadView.ESPECIALIDADES);
    }

    @Test
    @DisplayName("sync: traduce cada fila de SysAcad a un comando y delega el upsert en SpecialtyService")
    void syncTranslatesRowsAndDelegatesToService() {
        when(catalogReader.findSpecialties())
                .thenReturn(List.of(new SysacadSpecialtyDto(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf.")));
        when(specialtyService.syncSpecialties(anyList())).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SpecialtySyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(specialtyService).syncSpecialties(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new SpecialtySyncCommand(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf."));
        verify(syncStateService).recordSuccess(SysacadView.ESPECIALIDADES, 1);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findSpecialties()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.ESPECIALIDADES, "SysAcad caído");
    }
}
