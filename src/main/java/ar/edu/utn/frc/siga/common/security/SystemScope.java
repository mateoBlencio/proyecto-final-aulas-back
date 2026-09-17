package ar.edu.utn.frc.siga.common.security;

import java.util.function.Supplier;

/**
 * Marca el hilo actual como "sistema": los caminos internos sin sesión de usuario (sync de SysAcad,
 * import de Excel, listeners de reconciliación) corren dentro de {@link #run}/{@link #call} y el
 * {@link BuildingScopeResolver} les da alcance irrestricto. Es explícito y greppable a propósito:
 * cada uso es un punto donde el chequeo por edificio se saltea deliberadamente.
 */
public final class SystemScope {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SystemScope() {}

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static <T> T call(Supplier<T> action) {
        boolean previous = isActive();
        ACTIVE.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            ACTIVE.set(previous);
        }
    }

    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
