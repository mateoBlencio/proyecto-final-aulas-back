package ar.edu.utn.frc.siga.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SystemScope")
class SystemScopeTest {

    @Test
    @DisplayName("fuera de run/call: inactivo")
    void inactiveByDefault() {
        assertThat(SystemScope.isActive()).isFalse();
    }

    @Test
    @DisplayName("call: activo dentro, restaurado al salir (incluso con excepción)")
    void activeInsideCallAndRestoredOnExit() {
        String result = SystemScope.call(() -> {
            assertThat(SystemScope.isActive()).isTrue();
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(SystemScope.isActive()).isFalse();

        try {
            SystemScope.run(() -> {
                assertThat(SystemScope.isActive()).isTrue();
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // esperado
        }
        assertThat(SystemScope.isActive()).isFalse();
    }

    @Test
    @DisplayName("call anidado: al salir del interno sigue activo el externo")
    void nestedCallsRestoreOuterState() {
        SystemScope.run(() -> {
            SystemScope.run(() -> assertThat(SystemScope.isActive()).isTrue());
            assertThat(SystemScope.isActive()).isTrue();
        });
        assertThat(SystemScope.isActive()).isFalse();
    }
}
