package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.audit.internal.AuditOperationContext;
import org.springframework.modulith.NamedInterface;

/** Acceso de otros módulos a la operación de auditoría en curso. */
@NamedInterface("api")
public final class AuditOperations {

    private AuditOperations() {
    }

    /** Id de la operación en curso en este hilo, o null fuera de una operación. */
    public static String currentOperationId() {
        return AuditOperationContext.currentOperationId();
    }
}
