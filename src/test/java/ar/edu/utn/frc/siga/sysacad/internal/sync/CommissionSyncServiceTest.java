package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
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
        service = new CommissionSyncService(commissionService, syncStateService, new SysacadProperties());
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
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 1, 2024, 55, 30, null)));
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(2);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30, null));
        verify(syncStateService).recordSuccess(SysacadView.COMISIONES, 2);
    }

    @Test
    @DisplayName("sync: varias franjas del mismo curso+materia no rompen el índice y se toma el mayor de inscriptos")
    void syncMergesRepeatedEnrollmentRowsKeepingHigherCount() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of(
                new SysacadSubjectCommissionDto("101", 1, 2024, 55, 12, null),
                new SysacadSubjectCommissionDto("101", 1, 2024, 55, 30, null),
                new SysacadSubjectCommissionDto("101", 1, 2024, 55, 7, null)));
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30, null));
    }

    @Test
    @DisplayName("sync: mismo curso y materia con distinto plan o especialidad toman cada uno su propio conteo, sin fusionar con el máximo")
    void syncKeepsSeparateEnrollmentPerSpecialtyAndStudyPlan() {
        when(catalogReader.findCommissions()).thenReturn(List.of(
                new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1),
                new SysacadCommissionDto("101", 1, 2008, 55, 2026, 1),
                new SysacadCommissionDto("101", 2, 2024, 55, 2026, 1)));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of(
                new SysacadSubjectCommissionDto("101", 1, 2024, 55, 12, null),
                new SysacadSubjectCommissionDto("101", 1, 2008, 55, 30, null),
                new SysacadSubjectCommissionDto("101", 2, 2024, 55, 7, null)));
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(3);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, 12, null),
                new CommissionSyncCommand("101", 1, 2008, 55, 2026, 30, null),
                new CommissionSyncCommand("101", 2, 2024, 55, 2026, 7, null));
    }

    @Test
    @DisplayName("sync: el conteo de otro plan o especialidad no se aplica a una comisión sin inscriptos propios")
    void syncDoesNotBorrowEnrollmentFromOtherStudyPlan() {
        when(catalogReader.findCommissions()).thenReturn(List.of(
                new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1)));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of(
                new SysacadSubjectCommissionDto("101", 1, 2008, 55, 30, null),
                new SysacadSubjectCommissionDto("101", 2, 2024, 55, 7, null)));
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, null, null));
    }

    @Test
    @DisplayName("sync: si no hay inscriptos para curso+materia, el comando lleva enrolledCount nulo")
    void syncBuildsCommandWithNullEnrolledCountWhenUnresolved() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions()).thenReturn(List.of());
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, null, null));
    }

    @Test
    @DisplayName("sync: copia el cuatrimestre de la pareja curso+materia al comando")
    void syncPropagatesSemesterToCommand() {
        SysacadCommissionDto row = new SysacadCommissionDto("101", 1, 2024, 55, 2026, 1);
        when(catalogReader.findCommissions()).thenReturn(List.of(row));
        when(catalogReader.findSubjectCommissions())
                .thenReturn(List.of(new SysacadSubjectCommissionDto("101", 1, 2024, 55, 30, 2)));
        when(commissionService.syncCommissions(anyList(), eq(50))).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionSyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commissionService).syncCommissions(captor.capture(), eq(50));
        assertThat(captor.getValue()).containsExactly(
                new CommissionSyncCommand("101", 1, 2024, 55, 2026, 30, 2));
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findCommissions()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(eq(SysacadView.COMISIONES), startsWith("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("sync: con la vista vacía no llama al servicio de dominio y registra el fallo")
    void syncWithEmptyViewSkipsDomainServiceAndRecordsFailure() {
        when(catalogReader.findCommissions()).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SysacadEmptyViewException.class);

        verify(commissionService, never()).syncCommissions(anyList(), eq(50));
        verify(syncStateService).recordFailure(eq(SysacadView.COMISIONES), anyString());
        verify(syncStateService, never()).recordSuccess(any(), anyInt());
    }
}
