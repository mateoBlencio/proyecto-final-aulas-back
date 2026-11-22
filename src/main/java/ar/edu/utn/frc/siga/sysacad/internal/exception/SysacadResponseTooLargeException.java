package ar.edu.utn.frc.siga.sysacad.internal.exception;

public class SysacadResponseTooLargeException extends RuntimeException {

    public SysacadResponseTooLargeException(long maxBytes) {
        super("La respuesta de SysAcad supera el máximo de " + maxBytes + " bytes");
    }
}
