package ar.edu.utn.frc.siga.audit;

import java.util.UUID;

/**
 * Contexto por hilo de la operación de negocio en curso (ver {@link AuditOperation}).
 * Lo escribe {@code AuditOperationAspect} al entrar/salir de un método anotado y lo lee
 * {@link SigaRevisionListener} al sellar cada revisión de Envers.
 *
 * <p>No se propaga a hilos hijos: los cambios auditados dentro de un
 * {@code @ApplicationModuleListener} (otro hilo) quedan sin operación asociada.
 */
public final class AuditOperationContext {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private AuditOperationContext() {
    }

    static void begin(String description) {
        Holder holder = CURRENT.get();
        if (holder == null) {
            CURRENT.set(new Holder(new Operation(UUID.randomUUID().toString(), description)));
        } else {
            holder.depth++;
        }
    }

    static void end() {
        Holder holder = CURRENT.get();
        if (holder == null) {
            return;
        }
        if (--holder.depth <= 0) {
            CURRENT.remove();
        }
    }

    static Operation current() {
        Holder holder = CURRENT.get();
        return holder == null ? null : holder.operation;
    }

    record Operation(String id, String description) {
    }

    private static final class Holder {

        private final Operation operation;
        private int depth = 1;

        private Holder(Operation operation) {
            this.operation = operation;
        }
    }
}
