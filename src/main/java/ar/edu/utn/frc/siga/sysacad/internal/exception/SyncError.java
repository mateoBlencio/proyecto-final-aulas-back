package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.client.RestClientResponseException;

/**
 * Texto del último error de una vista: código + mensaje propio, sin el mensaje crudo de la excepción
 * (puede traer SQL, clases internas o el cuerpo de la respuesta de Sysacad). El detalle va al log.
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
