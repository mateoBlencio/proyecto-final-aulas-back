package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.AuditCause;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Opens the operation context around methods annotated with {@code @AuditOperation}.
 * <p>
 * Limitation: if the annotated method joins an outer transaction, its revisions are stamped at the
 * outer commit, after {@code end()} already cleared the context, so they get no {@code operacion_id}
 * (and no description update applies).
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditOperationAspect {

    private final RevisionDescriptionUpdater descriptionUpdater;

    @Around("@annotation(ar.edu.utn.frc.siga.audit.AuditOperation)")
    public Object aroundAuditOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuditOperation auditOperation = signature.getMethod().getAnnotation(AuditOperation.class);
        AuditOperationContext.begin(auditOperation.value(), originOperationId(joinPoint.getArgs()));
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            // A description set before the failure (e.g. "failed after N rows") still applies to the
            // revisions committed by earlier REQUIRES_NEW steps; if everything rolled back it updates 0 rows.
            AuditOperationContext.PendingDescription failed = AuditOperationContext.end();
            if (failed != null) {
                descriptionUpdater.update(failed.operationId(), failed.description());
            }
            throw t;
        }
        // The aspect has the highest precedence: the method transaction (and any REQUIRES_NEW one) is
        // already committed here, so every revision of the operation is visible to the UPDATE.
        AuditOperationContext.PendingDescription pending = AuditOperationContext.end();
        if (pending != null) {
            descriptionUpdater.update(pending.operationId(), pending.description());
        }
        return result;
    }

    private static String originOperationId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuditCause cause && cause.originOperationId() != null) {
                return cause.originOperationId();
            }
        }
        return null;
    }
}
