package ar.edu.utn.frc.siga.common.exception;

import java.io.Serial;

/** An external sync is aborted because the incoming data is not reliable enough to deactivate anything. */
public class SyncAbortedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SyncAbortedException(String message) {
        super(message);
    }
}
