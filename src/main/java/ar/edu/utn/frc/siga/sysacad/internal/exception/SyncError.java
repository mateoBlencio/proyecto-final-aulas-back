package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.client.RestClientResponseException;

/**
 * Last-error text of a view: a code plus our own message, never the raw exception message (it may carry SQL,
 * internal class names or the Sysacad response body). The detail goes to the log.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SyncError {

    public static String describe(Throwable e) {
        if (e instanceof SysacadUnavailableException) {
            return "CONNECTIVITY: No se pudo conectar con SysAcad";
        }
        if (e instanceof RestClientResponseException http) {
            return "UPSTREAM_HTTP_ERROR: SysAcad respondió con estado " + http.getStatusCode().value();
        }
        if (e instanceof SysacadResponseTooLargeException) {
            return "RESPONSE_TOO_LARGE: La respuesta de SysAcad supera el tamaño máximo permitido";
        }
        if (e instanceof SysacadEmptyViewException) {
            return "EMPTY_VIEW: La vista vino vacía";
        }
        if (e instanceof SyncAbortedException) {
            return "SYNC_ABORTED: " + e.getMessage();
        }
        return "INTERNAL_ERROR: Error inesperado al sincronizar la vista; el detalle está en el log del servidor";
    }

    public static String dependencyFailed(SysacadView dependency) {
        return "DEPENDENCY_FAILED: No se ejecutó porque falló la vista " + dependency + " en esta corrida";
    }
}
