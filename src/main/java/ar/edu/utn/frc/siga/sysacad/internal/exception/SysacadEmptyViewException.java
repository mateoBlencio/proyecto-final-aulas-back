package ar.edu.utn.frc.siga.sysacad.internal.exception;

public class SysacadEmptyViewException extends RuntimeException {

    public SysacadEmptyViewException(String viewLabel) {
        super("Los datos de " + viewLabel + " de SysAcad vinieron vacíos: se aborta el sync para no marcar todo como ausente");
    }
}
