package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Guarda previa a dar de baja lo ausente en un sync: evita bajas masivas ante un origen degradado. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SyncGuard {

    /**
     * Aborta si no llegó ninguna fila válida, o si llegaron menos que {@code minValidPercent}% de las
     * filas hoy vigentes. {@code minValidPercent = 0} desactiva solo el umbral; cero filas válidas aborta siempre.
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
