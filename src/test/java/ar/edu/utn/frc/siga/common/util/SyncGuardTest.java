package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SyncGuard")
class SyncGuardTest {

    @Test
    @DisplayName("cero filas válidas aborta siempre, incluso con el umbral desactivado y sin vigentes")
    void zeroValidRowsAlwaysAborts() {
        assertThatThrownBy(() -> SyncGuard.requireEnoughValidRows("aulas", 0, 0, 0))
                .isInstanceOf(SyncAbortedException.class);
        assertThatThrownBy(() -> SyncGuard.requireEnoughValidRows("aulas", 0, 100, 50))
                .isInstanceOf(SyncAbortedException.class);
    }

    @Test
    @DisplayName("una fila menos que el umbral aborta; justo en el umbral pasa")
    void thresholdBoundary() {
        assertThatThrownBy(() -> SyncGuard.requireEnoughValidRows("aulas", 49, 100, 50))
                .isInstanceOf(SyncAbortedException.class)
                .hasMessageContaining("49")
                .hasMessageContaining("100");
        assertThatCode(() -> SyncGuard.requireEnoughValidRows("aulas", 50, 100, 50)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("umbral 0 desactiva solo el porcentaje")
    void zeroThresholdDisablesPercentage() {
        assertThatCode(() -> SyncGuard.requireEnoughValidRows("aulas", 1, 10_000, 0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin vigentes cualquier cantidad válida pasa (primera corrida)")
    void firstRunWithoutCurrentRows() {
        assertThatCode(() -> SyncGuard.requireEnoughValidRows("aulas", 3, 0, 50)).doesNotThrowAnyException();
    }
}
