package ar.edu.utn.frc.siga.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Catálogo de Permission")
class PermissionCatalogTest {

    @Test
    @DisplayName("todo Permission declara un ScopeType no nulo")
    void todoPermisoDeclaraScopeType() {
        assertThat(Permission.values()).allSatisfy(permission ->
                assertThat(permission.scopeType()).isNotNull());
    }

    @Test
    @DisplayName("Authorities.of: no genera nombres duplicados entre permisos distintos")
    void authoritiesNoGeneraDuplicados() {
        Set<String> names = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertThat(names.add(Authorities.of(permission)))
                    .as("authority duplicada para " + permission)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Authorities.of: sigue la convención PERM_<NOMBRE>")
    void authoritiesSigueLaConvencion() {
        assertThat(Authorities.of(Permission.CLASSROOM_READ)).isEqualTo("PERM_CLASSROOM_READ");
    }

    @Test
    @DisplayName("no hay permisos con nombre repetido en el enum (regresión trivial)")
    void noHayNombresRepetidos() {
        long distinctCount = Arrays.stream(Permission.values()).map(Permission::name).distinct().count();
        assertThat(distinctCount).isEqualTo(Permission.values().length);
    }
}
