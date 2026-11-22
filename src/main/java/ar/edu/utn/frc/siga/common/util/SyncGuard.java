package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Guard run before deactivating whatever is absent in a sync: prevents mass deactivation from a degraded source. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SyncGuard {

    /**
     * Aborts if no valid row arrived, or if fewer than {@code minValidPercent}% of the currently active rows
     * arrived. {@code minValidPercent = 0} disables only the percentage; zero valid rows always aborts.
     */
    public static void requireEnoughValidRows(String entity, long validRows, long currentRows, int minValidPercent) {
        if (validRows == 0) {
            throw new SyncAbortedException("No llegó ninguna fila válida de " + entity
                    + ": se aborta el sync sin dar de baja nada");
        }
        if (minValidPercent > 0 && validRows * 100 < (long) minValidPercent * currentRows) {
            throw new SyncAbortedException("Llegaron " + validRows + " filas válidas de " + entity + " contra "
                    + currentRows + " vigentes (mínimo " + minValidPercent + "%): se aborta el sync sin dar de baja nada");
        }
    }
}
