package ar.edu.utn.frc.siga.audit.internal;

import java.util.UUID;

/**
 * Contexto por hilo de la operación de negocio en curso (ver {@link AuditOperation}).
 * Lo escribe {@code AuditOperationAspect} al entrar/salir de un método anotado y lo lee
 * {@link SigaRevisionListener} al sellar cada revisión de Envers.
 *
 * <p>The context is not propagated across threads: an async listener needs its own
 * {@link AuditOperation}. The cause is propagated explicitly: the event that crosses threads
 * implements {@code AuditCause} and carries the id of the operation that published it, which
 * becomes the {@code parentId} of the listener's operation.
 */
public final class AuditOperationContext {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private AuditOperationContext() {
    }

    static void begin(String description, String parentId) {
        Holder holder = CURRENT.get();
        if (holder == null) {
            CURRENT.set(new Holder(UUID.randomUUID().toString(), description, parentId));
        } else {
            holder.depth++;
        }
    }

    /**
     * Closes one level. When it closes the outermost one and the description changed after the
     * first revision was stamped, returns the pending {@code revinfo} rewrite; otherwise null.
     */
    static PendingDescription end() {
        Holder holder = CURRENT.get();
        if (holder == null) {
            return null;
        }
        if (--holder.depth > 0) {
            return null;
        }
        CURRENT.remove();
        return holder.dirty ? new PendingDescription(holder.id, holder.description) : null;
    }

    static Operation current() {
        Holder holder = CURRENT.get();
        return holder == null ? null : new Operation(holder.id, holder.description, holder.parentId);
    }

    /**
     * Replaces the description of the current operation. It does nothing outside an operation or
     * inside a nested {@link AuditOperation} (depth above 1): the outermost operation is the one
     * the user triggered, and an inner service must not overwrite its text.
     */
    public static void describe(String description) {
        Holder holder = CURRENT.get();
        if (holder == null || holder.depth > 1 || description == null || description.isBlank()) {
            return;
        }
        holder.description = truncate(description);
        if (holder.stamped) {
            holder.dirty = true;
        }
    }

    /** Called by {@link SigaRevisionListener}: from now on a description change needs an UPDATE. */
    static void markStamped() {
        Holder holder = CURRENT.get();
        if (holder != null) {
            holder.stamped = true;
        }
    }

    /** Fits {@code revinfo.descripcion} (varchar 255) without splitting a surrogate pair. */
    static String truncate(String text) {
        if (text.length() <= MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        int end = MAX_DESCRIPTION_LENGTH - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    public static String currentOperationId() {
        Operation operation = current();
        return operation == null ? null : operation.id();
    }

    record Operation(String id, String description, String parentId) {
    }

    record PendingDescription(String operationId, String description) {
    }

    private static final class Holder {

        private final String id;
        private final String parentId;
        private String description;
        private int depth = 1;
        private boolean stamped;
        private boolean dirty;

        private Holder(String id, String description, String parentId) {
            this.id = id;
            this.description = description;
            this.parentId = parentId;
        }
    }
}
