package ar.edu.utn.frc.siga.sysacad.internal;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadSyncOrchestrator")
class SysacadSyncOrchestratorTest {

    @Mock
    private SysacadViewSyncer buildings;
    @Mock
    private SysacadViewSyncer classrooms;
    @Mock
    private SysacadViewSyncer specialties;
    @Mock
    private SysacadViewSyncer commissions;

    private SysacadSyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(buildings.view()).thenReturn(SysacadView.EDIFICIOS);
        when(classrooms.view()).thenReturn(SysacadView.AULAS);
        when(specialties.view()).thenReturn(SysacadView.ESPECIALIDADES);
        when(commissions.view()).thenReturn(SysacadView.COMISIONES);
        // El orden de registro es el inverso al de FK para probar que no lo define la inyección.
        orchestrator = new SysacadSyncOrchestrator(List.of(commissions, specialties, classrooms, buildings));
    }

    @Test
    @DisplayName("Resincroniza todas las vistas respetando el orden de FK")
    void resyncAll_respectsForeignKeyOrder() {
        orchestrator.resyncAll();

        InOrder order = inOrder(buildings, classrooms, specialties, commissions);
        order.verify(buildings).sync();
        order.verify(classrooms).sync();
        order.verify(specialties).sync();
        order.verify(commissions).sync();
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Una vista que falla no frena el resync de las siguientes")
    void resyncAll_continuesAfterFailedView() {
        doThrow(new IllegalStateException("SysAcad no responde")).when(buildings).sync();

        orchestrator.resyncAll();

        verify(classrooms).sync();
        verify(specialties).sync();
        verify(commissions).sync();
    }

    @Test
    @DisplayName("Sin fallos, el resultado es SUCCESS")
    void resyncAll_allOk_returnsSuccess() {
        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.SUCCESS);
    }

    @Test
    @DisplayName("Una vista con error de datos (no de conexión) da PARTIAL_FAILURE")
    void resyncAll_dataError_returnsPartialFailure() {
        doThrow(new IllegalStateException("dato inválido")).when(buildings).sync();

        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.PARTIAL_FAILURE);
    }

    @Test
    @DisplayName("Todas las vistas fallando por conexión da CONNECTIVITY_FAILURE")
    void resyncAll_allConnectivityErrors_returnsConnectivityFailure() {
        doThrow(new SysacadUnavailableException("I/O error", null)).when(buildings).sync();
        doThrow(new SysacadUnavailableException("I/O error", null)).when(classrooms).sync();
        doThrow(new SysacadUnavailableException("I/O error", null)).when(specialties).sync();
        doThrow(new SysacadUnavailableException("I/O error", null)).when(commissions).sync();

        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.CONNECTIVITY_FAILURE);
    }

    @Test
    @DisplayName("Conexión caída en una vista y otra con error de datos da PARTIAL_FAILURE")
    void resyncAll_mixedErrors_returnsPartialFailure() {
        doThrow(new SysacadUnavailableException("I/O error", null)).when(buildings).sync();
        doThrow(new IllegalStateException("dato inválido")).when(classrooms).sync();

        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.PARTIAL_FAILURE);
    }

    @Test
    @DisplayName("Dos disparos concurrentes no corren el sync dos veces")
    void resyncAll_ignoresConcurrentTrigger() throws Exception {
        CountDownLatch firstRunStarted = new CountDownLatch(1);
        CountDownLatch firstRunReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstRunStarted.countDown();
            firstRunReleased.await();
            return null;
        }).when(buildings).sync();

        Thread firstRun = new Thread(orchestrator::resyncAll);
        firstRun.start();
        firstRunStarted.await();

        orchestrator.resyncAll();

        firstRunReleased.countDown();
        firstRun.join();

        verify(buildings, times(1)).sync();
        verify(classrooms, times(1)).sync();
        verify(specialties, times(1)).sync();
        verify(commissions, times(1)).sync();
    }
}
