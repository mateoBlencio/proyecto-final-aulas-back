package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.internal.AuditDescriptionProbe;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomMovementPublisher")
class ClassroomMovementPublisherTest {

    private static final List<Change> CHANGES = List.of(new Change(10L, 3L, LocalDate.of(2026, 10, 20),
            LocalTime.of(8, 0), LocalTime.of(9, 30), 5L, 7L));

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClassroomMovementPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ClassroomMovementPublisher(eventPublisher);
    }

    @AfterEach
    void clearSecurityContextAndTransaction() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.unbindResourceIfPossible(publisher);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("lista vacia: no publica aunque haya un usuario autenticado")
    void doesNotPublishEmptyChanges() {
        authenticateAs("sub@utn.edu.ar");

        publisher.publish(AllocationSource.MANUAL, "obs", List.of());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("SystemScope activo (import, sync, listeners): no publica")
    void doesNotPublishInsideSystemScope() {
        authenticateAs("sub@utn.edu.ar");

        SystemScope.run(() -> publisher.publish(AllocationSource.IMPORTED, "obs", CHANGES));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("sin autenticacion en el contexto: no publica")
    void doesNotPublishWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        publisher.publish(AllocationSource.MANUAL, "obs", CHANGES);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("autenticacion no confirmada: no publica")
    void doesNotPublishWithUnauthenticatedToken() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("sub@utn.edu.ar", "x"));

        publisher.publish(AllocationSource.MANUAL, "obs", CHANGES);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("usuario anonimo: no publica")
    void doesNotPublishForAnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        publisher.publish(AllocationSource.MANUAL, "obs", CHANGES);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("usuario autenticado fuera de una operacion de auditoria: publica con el actor y un operationId generado")
    void publishesWithActorAndGeneratedOperationId() {
        authenticateAs("sub@utn.edu.ar");

        publisher.publish(AllocationSource.MANUAL, "obra", CHANGES);

        ClassroomMovements event = capturePublished();
        assertThat(event.actor()).isEqualTo("sub@utn.edu.ar");
        assertThat(event.source()).isEqualTo(AllocationSource.MANUAL);
        assertThat(event.reason()).isEqualTo("obra");
        assertThat(event.changes()).isEqualTo(CHANGES);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(UUID.fromString(event.operationId())).isNotNull();
    }

    @Test
    @DisplayName("dentro de una operacion de auditoria: el operationId del evento es el de la operacion")
    void publishesWithAuditOperationId() {
        authenticateAs("sub@utn.edu.ar");
        OperationRunner runner = new AuditDescriptionProbe().audited(new OperationRunner());
        String[] operationId = new String[1];

        runner.run(() -> {
            operationId[0] = ar.edu.utn.frc.siga.audit.AuditOperations.currentOperationId();
            publisher.publish(AllocationSource.MANUAL, "obs", CHANGES);
        });

        assertThat(operationId[0]).isNotNull();
        assertThat(capturePublished().operationId()).isEqualTo(operationId[0]);
    }

    @Test
    @DisplayName("con transaccion: no publica hasta el beforeCommit")
    void publishesOnlyAtBeforeCommit() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "obra", CHANGES);

        verifyNoInteractions(eventPublisher);
        assertThat(TransactionSynchronizationManager.getResource(publisher)).isNotNull();

        commit();

        assertThat(capturePublished().changes()).isEqualTo(CHANGES);
        assertThat(TransactionSynchronizationManager.getResource(publisher)).isNull();
    }

    @Test
    @DisplayName("dos publish en la misma transaccion: un solo evento con ambas ocurrencias")
    void twoPublishesInSameTransactionYieldOneEvent() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();
        Change other = change(11L, 6L, 8L);

        publisher.publish(AllocationSource.MANUAL, "a", CHANGES);
        publisher.publish(AllocationSource.MANUAL, "b", List.of(other));
        commit();

        assertThat(capturePublished().changes()).containsExactly(CHANGES.getFirst(), other);
    }

    @Test
    @DisplayName("liberar el aula 5 y reasignar el aula 5 a la misma ocurrencia: no publica")
    void releaseAndReassignToSameClassroomPublishesNothing() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "libera", List.of(change(10L, 5L, null)));
        publisher.publish(AllocationSource.MANUAL, "reasigna", List.of(change(10L, null, 5L)));
        commit();

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
        assertThat(TransactionSynchronizationManager.getResource(publisher)).isNull();
    }

    @Test
    @DisplayName("liberar y reasignar a otra aula: una linea con el primer from y el ultimo to")
    void mergeTakesFirstFromAndLastTo() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "libera", List.of(change(10L, 5L, null)));
        publisher.publish(AllocationSource.MANUAL, "reasigna", List.of(change(10L, null, 9L)));
        commit();

        assertThat(capturePublished().changes()).containsExactly(change(10L, 5L, 9L));
    }

    @Test
    @DisplayName("tres movimientos de la misma ocurrencia: primer from, ultimo to y resto de campos de la ultima aparicion")
    void mergeUsesLastOccurrenceFields() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();
        Change last = new Change(10L, 4L, LocalDate.of(2026, 10, 21), LocalTime.of(10, 0), LocalTime.of(11, 30),
                7L, 9L);

        publisher.publish(AllocationSource.MANUAL, "a", List.of(change(10L, 5L, 6L)));
        publisher.publish(AllocationSource.MANUAL, "b", List.of(change(10L, 6L, 7L)));
        publisher.publish(AllocationSource.MANUAL, "c", List.of(last));
        commit();

        assertThat(capturePublished().changes()).containsExactly(new Change(10L, 4L, LocalDate.of(2026, 10, 21),
                LocalTime.of(10, 0), LocalTime.of(11, 30), 5L, 9L));
    }

    @Test
    @DisplayName("una ocurrencia que vuelve al origen se descarta y las demas se publican")
    void discardsOnlyOccurrencesThatReturnToOrigin() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();
        Change kept = change(11L, 6L, 8L);

        publisher.publish(AllocationSource.MANUAL, "a", List.of(change(10L, 5L, 7L), kept));
        publisher.publish(AllocationSource.MANUAL, "b", List.of(change(10L, 7L, 5L)));
        commit();

        assertThat(capturePublished().changes()).containsExactly(kept);
    }

    @Test
    @DisplayName("gana el reason de la reasignacion sobre el del release, con su source")
    void reasonOfReassignmentWinsOverRelease() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "libera", List.of(change(10L, 5L, null)));
        publisher.publish(AllocationSource.AUTOMATIC, "reasigna", List.of(change(10L, null, 9L)));
        commit();

        ClassroomMovements event = capturePublished();
        assertThat(event.reason()).isEqualTo("reasigna");
        assertThat(event.source()).isEqualTo(AllocationSource.AUTOMATIC);
    }

    @Test
    @DisplayName("si el primero trae reason y el segundo no, se conserva el primero con su source")
    void firstReasonIsKeptWhenSecondHasNone() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "libera", List.of(change(10L, 5L, null)));
        publisher.publish(AllocationSource.AUTOMATIC, null, List.of(change(10L, null, 9L)));
        commit();

        ClassroomMovements event = capturePublished();
        assertThat(event.reason()).isEqualTo("libera");
        assertThat(event.source()).isEqualTo(AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("si ninguna publicacion trae reason: reason null y source de la ultima")
    void noReasonKeepsLastSource() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, null, List.of(change(10L, 5L, null)));
        publisher.publish(AllocationSource.AUTOMATIC, null, List.of(change(10L, null, 9L)));
        commit();

        ClassroomMovements event = capturePublished();
        assertThat(event.reason()).isNull();
        assertThat(event.source()).isEqualTo(AllocationSource.AUTOMATIC);
    }

    @Test
    @DisplayName("rollback: no publica y libera el recurso de la transaccion")
    void rollbackPublishesNothingAndReleasesResource() {
        authenticateAs("sub@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(AllocationSource.MANUAL, "obra", CHANGES);
        assertThat(TransactionSynchronizationManager.getResource(publisher)).isNotNull();
        rollback();

        verifyNoInteractions(eventPublisher);
        assertThat(TransactionSynchronizationManager.getResource(publisher)).isNull();
    }

    @Test
    @DisplayName("operationId y actor son los de la primera publicacion")
    void operationIdAndActorComeFromFirstPublish() {
        authenticateAs("primero@utn.edu.ar");
        TransactionSynchronizationManager.initSynchronization();
        OperationRunner runner = new AuditDescriptionProbe().audited(new OperationRunner());
        String[] ids = new String[2];

        runner.run(() -> {
            ids[0] = ar.edu.utn.frc.siga.audit.AuditOperations.currentOperationId();
            publisher.publish(AllocationSource.MANUAL, "a", List.of(change(10L, 5L, null)));
        });
        authenticateAs("segundo@utn.edu.ar");
        runner.run(() -> {
            ids[1] = ar.edu.utn.frc.siga.audit.AuditOperations.currentOperationId();
            publisher.publish(AllocationSource.MANUAL, "b", List.of(change(10L, null, 9L)));
        });
        commit();

        assertThat(ids[0]).isNotNull().isNotEqualTo(ids[1]);
        ClassroomMovements event = capturePublished();
        assertThat(event.operationId()).isEqualTo(ids[0]);
        assertThat(event.actor()).isEqualTo("primero@utn.edu.ar");
    }

    private static Change change(Long occurrenceId, Long from, Long to) {
        return new Change(occurrenceId, 3L, LocalDate.of(2026, 10, 20), LocalTime.of(8, 0), LocalTime.of(9, 30),
                from, to);
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(sync -> sync.beforeCommit(false));
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private static void rollback() {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private ClassroomMovements capturePublished() {
        ArgumentCaptor<ClassroomMovements> captor = ArgumentCaptor.forClass(ClassroomMovements.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("PERM_ALLOCATION_WRITE"))));
    }

    public static class OperationRunner {

        @AuditOperation("Operacion de prueba")
        public void run(Runnable action) {
            action.run();
        }
    }
}
