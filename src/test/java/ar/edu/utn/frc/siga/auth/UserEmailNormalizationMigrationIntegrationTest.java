package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("V65 correo de usuario único sin distinguir mayúsculas (integración)")
class UserEmailNormalizationMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("después de las migraciones existe uq_usuario_correo_lower sobre lower(correo) y no existe usuario_correo_key")
    void indexReplacesCaseSensitiveConstraint() {
        String indexDef = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'usuario' AND indexname = 'uq_usuario_correo_lower'",
                String.class);
        assertThat(indexDef).startsWith("CREATE UNIQUE INDEX").contains("lower((correo)::text)");

        Integer constraints = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'usuario_correo_key'", Integer.class);
        assertThat(constraints).isZero();
    }

    @Test
    @DisplayName("la base rechaza un segundo usuario cuyo correo solo difiere en mayúsculas")
    void databaseRejectsCaseOnlyDuplicate() {
        String lower = "v65.dup" + System.nanoTime() + "@frc.utn.edu.ar";
        insertUser(lower);

        assertThatThrownBy(() -> insertUser(lower.toUpperCase(java.util.Locale.ROOT)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_usuario_correo_lower");
    }

    private void insertUser(String email) {
        jdbc.update("INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido) "
                + "VALUES (?, 'HASH', true, 'V65', 'Test')", email);
    }
}
