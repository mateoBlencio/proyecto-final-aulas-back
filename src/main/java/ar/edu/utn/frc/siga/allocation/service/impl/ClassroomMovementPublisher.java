package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Junta los cambios de aula de toda la transacción y publica un único {@link ClassroomMovements} antes del commit.
 * Una operación puede pasar por el writer varias veces (p. ej. liberar y reasignar) con el mismo operationId, y el
 * listener solo acepta un evento por operación.
 */
@Component
@RequiredArgsConstructor
class ClassroomMovementPublisher {

    private final ApplicationEventPublisher eventPublisher;

    void publish(AllocationSource source, String reason, List<Change> changes) {
        if (changes.isEmpty() || SystemScope.isActive()) return;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Pending pending = new Pending(operationId(), authentication.getName());
            pending.add(source, reason, changes);
            publishPending(pending);
            return;
        }

        Pending pending = (Pending) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            pending = new Pending(operationId(), authentication.getName());
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                // Antes del commit para que el registro de publicación de Modulith quede en la misma transacción.
                @Override
                public void beforeCommit(boolean readOnly) {
                    Pending toPublish = (Pending) TransactionSynchronizationManager.unbindResourceIfPossible(
                            ClassroomMovementPublisher.this);
                    if (toPublish != null) publishPending(toPublish);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(ClassroomMovementPublisher.this);
                }
            });
        }
        pending.add(source, reason, changes);
    }

    private static String operationId() {
        String operationId = AuditOperations.currentOperationId();
        return operationId != null ? operationId : UUID.randomUUID().toString();
    }

    private void publishPending(Pending pending) {
        List<Change> merged = pending.mergedChanges();
        if (merged.isEmpty()) return;
        eventPublisher.publishEvent(new ClassroomMovements(pending.operationId, pending.actor, pending.source,
                pending.reason, Instant.now(), merged));
    }

    private static final class Pending {
        private final String operationId;
        private final String actor;
        private final Map<Long, Change> byOccurrence = new LinkedHashMap<>();
        private AllocationSource source;
        private String reason;

        private Pending(String operationId, String actor) {
            this.operationId = operationId;
            this.actor = actor;
        }

        // Si no hay motivo, source queda con el de la última publicación.
        private void add(AllocationSource newSource, String newReason, List<Change> changes) {
            if (newReason != null || reason == null) {
                source = newSource;
                reason = newReason;
            }
            for (Change change : changes) {
                byOccurrence.merge(change.occurrenceId(), change, (first, last) -> new Change(
                        last.occurrenceId(), last.eventId(), last.date(), last.startTime(), last.endTime(),
                        first.fromClassroomId(), last.toClassroomId()));
            }
        }

        // Se fusiona por ocurrencia (primer from, último to) y se descartan los que vuelven al aula de origen.
        private List<Change> mergedChanges() {
            return byOccurrence.values().stream()
                    .filter(change -> !Objects.equals(change.fromClassroomId(), change.toClassroomId()))
                    .toList();
        }
    }
}
