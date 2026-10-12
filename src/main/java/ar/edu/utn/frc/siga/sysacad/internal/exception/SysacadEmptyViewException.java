package ar.edu.utn.frc.siga.sysacad.internal.exception;

public class SysacadEmptyViewException extends RuntimeException {

    public SysacadEmptyViewException(String viewName) {
        super("La vista " + viewName + " de SysAcad vino vacía: se aborta el sync para no marcar todo como ausente");
    }
}
