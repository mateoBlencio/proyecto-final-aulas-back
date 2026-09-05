package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
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
@DisplayName("CommissionSyncService (thin syncer)")
class CommissionSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private CommissionService commissionService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private CommissionSyncService service;

    @BeforeEach
    void setUp() {
        service = new CommissionSyncService(commissionService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista COMISIONES")
    void viewReturnsComisiones() {
        assertThat(service.view()).isEqualTo(SysacadView.COMISIONES);
    }

    @Test
    @DisplayName("sync: enlaza cada comisión con sus inscriptos por curso+materia y delega el upsert en CommissionService")
    void syncJoinsEnrollmentsAndDelegatesToService() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions())
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 55, 30)));
        when(commissionService.syncCommissions(anyList())).thenReturn(2);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30));
        verify(syncStateService).recordSuccess(SysacadView.COMISIONES, 2);
    }

    @Test
    @DisplayName("sync: si no hay inscriptos para curso+materia, el comando lleva enrolledCount nulo")
    void syncBuildsCommandWithNullEnrolledCountWhenUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of());
        when(commissionService.syncCommissions(anyList())).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, null));
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findCommissions()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.COMISIONES, "SysAcad caído");
    }
}
