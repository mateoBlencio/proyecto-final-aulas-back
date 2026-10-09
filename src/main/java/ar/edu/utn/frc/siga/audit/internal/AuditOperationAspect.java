package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.AuditCause;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditOperationAspect {

    @Around("@annotation(ar.edu.utn.frc.siga.audit.AuditOperation)")
    public Object aroundAuditOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuditOperation auditOperation = signature.getMethod().getAnnotation(AuditOperation.class);
        AuditOperationContext.begin(auditOperation.value(), originOperationId(joinPoint.getArgs()));
        try {
            return joinPoint.proceed();
        } finally {
            AuditOperationContext.end();
        }
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
