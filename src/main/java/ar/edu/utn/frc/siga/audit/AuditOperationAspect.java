package ar.edu.utn.frc.siga.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditOperationAspect {

    @Around("@annotation(auditOperation)")
    public Object aroundAuditOperation(ProceedingJoinPoint joinPoint, AuditOperation auditOperation) throws Throwable {
        AuditOperationContext.begin(auditOperation.value());
        try {
            return joinPoint.proceed();
        } finally {
            AuditOperationContext.end();
        }
    }
}
