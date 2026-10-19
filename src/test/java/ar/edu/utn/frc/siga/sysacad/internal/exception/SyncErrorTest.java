package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SyncError")
class SyncErrorTest {

    @Test
    @DisplayName("una excepción desconocida no filtra su mensaje (SQL, clases, cuerpo upstream)")
    void unknownExceptionDoesNotLeakMessage() {
        String text = SyncError.describe(new IllegalStateException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"uq_x\"]"));

        assertThat(text).startsWith("INTERNAL_ERROR:")
                .doesNotContain("duplicate key").doesNotContain("uq_x");
    }

    @Test
    @DisplayName("un error HTTP upstream informa solo el estado, no el cuerpo")
    void httpErrorReportsOnlyStatus() {
        String text = SyncError.describe(HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", null, "secret body with stack".getBytes(), null));

        assertThat(text).isEqualTo("UPSTREAM_HTTP_ERROR: SysAcad respondió con estado 502");
    }

    @Test
    @DisplayName("conectividad, vista vacía y sync abortado tienen código propio")
    void ownCodes() {
        assertThat(SyncError.describe(new SysacadUnavailableException("io /10.0.0.1", null)))
                .startsWith("CONNECTIVITY:").doesNotContain("10.0.0.1");
        assertThat(SyncError.describe(new SysacadEmptyViewException("AULAS"))).startsWith("EMPTY_VIEW:");
        assertThat(SyncError.describe(new SysacadResponseTooLargeException(10))).startsWith("RESPONSE_TOO_LARGE:");
        assertThat(SyncError.describe(new SyncAbortedException("llegaron pocas filas")))
                .isEqualTo("SYNC_ABORTED: llegaron pocas filas");
        assertThat(SyncError.dependencyFailed(SysacadView.EDIFICIOS))
                .startsWith("DEPENDENCY_FAILED:").contains("EDIFICIOS");
    }
}
