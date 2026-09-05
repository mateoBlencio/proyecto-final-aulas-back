package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;

import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadSyncOrchestratorImpl")
class SysacadSyncOrchestratorImplTest {

    @Mock
    private SysacadViewSyncer buildings;
    @Mock
    private SysacadViewSyncer classrooms;
    @Mock
    private SysacadViewSyncer specialties;
    @Mock
    private SysacadViewSyncer subjects;
    @Mock
    private SysacadViewSyncer commissions;
    @Mock
    private SysacadCatalogSnapshotFactory snapshotFactory;

    private final SysacadCatalogReader snapshot = mock(SysacadCatalogReader.class);

    private SysacadSyncOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        when(buildings.view()).thenReturn(SysacadView.EDIFICIOS);
        when(classrooms.view()).thenReturn(SysacadView.AULAS);
        when(specialties.view()).thenReturn(SysacadView.ESPECIALIDADES);
        when(subjects.view()).thenReturn(SysacadView.MATERIAS);
        when(commissions.view()).thenReturn(SysacadView.COMISIONES);
        when(snapshotFactory.newSnapshot()).thenReturn(snapshot);
        // El orden de registro es el inverso al de FK para probar que no lo define la inyección.
        orchestrator = new SysacadSyncOrchestratorImpl(
                List.of(commissions, subjects, specialties, classrooms, buildings), snapshotFactory);
    }

    @Test
    @DisplayName("Resincroniza todas las vistas respetando el orden de FK")
    void resyncAll_respectsForeignKeyOrder() {
        orchestrator.resyncAll();

        InOrder order = inOrder(buildings, classrooms, specialties, subjects, commissions);
        order.verify(buildings).sync(any());
        order.verify(classrooms).sync(any());
        order.verify(specialties).sync(any());
        order.verify(subjects).sync(any());
        order.verify(commissions).sync(any());
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("resyncAll arma un único snapshot y lo pasa a todos los syncers registrados")
    void resyncAll_buildsSingleSnapshotSharedByAllSyncers() {
        orchestrator.resyncAll();

        verify(snapshotFactory, times(1)).newSnapshot();
        verify(buildings).sync(snapshot);
        verify(classrooms).sync(snapshot);
        verify(specialties).sync(snapshot);
        verify(subjects).sync(snapshot);
        verify(commissions).sync(snapshot);
    }

    @Test
    @DisplayName("sync(view) arma un snapshot nuevo por cada invocación")
    void sync_buildsOneSnapshotPerInvocation() {
        orchestrator.sync(SysacadView.EDIFICIOS);
        orchestrator.sync(SysacadView.AULAS);

        verify(snapshotFactory, times(2)).newSnapshot();
        verify(buildings).sync(snapshot);
        verify(classrooms).sync(snapshot);
    }

    @Test
    @DisplayName("Una vista que falla no frena el resync de las siguientes")
    void resyncAll_continuesAfterFailedView() {
        doThrow(new IllegalStateException("SysAcad no responde")).when(buildings).sync(any());

        orchestrator.resyncAll();

        verify(classrooms).sync(any());
        verify(specialties).sync(any());
        verify(subjects).sync(any());
        verify(commissions).sync(any());
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
        doThrow(new IllegalStateException("dato inválido")).when(buildings).sync(any());

        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.PARTIAL_FAILURE);
    }

    @Test
    @DisplayName("Todas las vistas fallando por conexión da CONNECTIVITY_FAILURE")
    void resyncAll_allConnectivityErrors_returnsConnectivityFailure() {
        doThrow(new SysacadUnavailableException("I/O error", null)).when(buildings).sync(any());
        doThrow(new SysacadUnavailableException("I/O error", null)).when(classrooms).sync(any());
        doThrow(new SysacadUnavailableException("I/O error", null)).when(specialties).sync(any());
        doThrow(new SysacadUnavailableException("I/O error", null)).when(subjects).sync(any());
        doThrow(new SysacadUnavailableException("I/O error", null)).when(commissions).sync(any());

        SysacadResyncOutcome outcome = orchestrator.resyncAll();

        assertThat(outcome).isEqualTo(SysacadResyncOutcome.CONNECTIVITY_FAILURE);
    }

    @Test
    @DisplayName("Conexión caída en una vista y otra con error de datos da PARTIAL_FAILURE")
    void resyncAll_mixedErrors_returnsPartialFailure() {
        doThrow(new SysacadUnavailableException("I/O error", null)).when(buildings).sync(any());
        doThrow(new IllegalStateException("dato inválido")).when(classrooms).sync(any());

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
        }).when(buildings).sync(any());

        Thread firstRun = new Thread(orchestrator::resyncAll);
        firstRun.start();
        firstRunStarted.await();

        orchestrator.resyncAll();

        firstRunReleased.countDown();
        firstRun.join();

        verify(buildings, times(1)).sync(any());
        verify(classrooms, times(1)).sync(any());
        verify(specialties, times(1)).sync(any());
        verify(subjects, times(1)).sync(any());
        verify(commissions, times(1)).sync(any());
    }
}
