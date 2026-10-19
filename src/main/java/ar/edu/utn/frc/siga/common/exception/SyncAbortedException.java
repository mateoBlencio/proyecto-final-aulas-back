package ar.edu.utn.frc.siga.common.exception;

import java.io.Serial;

/** Se aborta un sync contra un origen externo porque los datos no son confiables para dar de baja. */
public class SyncAbortedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SyncAbortedException(String message) {
        super(message);
    }
}
