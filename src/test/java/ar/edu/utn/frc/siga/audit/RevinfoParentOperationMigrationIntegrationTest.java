package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V16 operacion_padre_id en revinfo (integración)")
class RevinfoParentOperationMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("la columna operacion_padre_id existe, es varchar(36) y admite null")
    void columnExists() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'revinfo' AND column_name = 'operacion_padre_id'");

        assertThat(columns).hasSize(1);
        assertThat(columns.getFirst().get("data_type")).isEqualTo("character varying");
        assertThat(columns.getFirst().get("character_maximum_length")).isEqualTo(36);
        assertThat(columns.getFirst().get("is_nullable")).isEqualTo("YES");
    }

    @Test
    @DisplayName("existe el índice idx_revinfo_operacion_padre_id sobre operacion_padre_id")
    void indexExists() {
        List<String> definitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'revinfo' "
                        + "AND indexname = 'idx_revinfo_operacion_padre_id'", String.class);

        assertThat(definitions).hasSize(1);
        assertThat(definitions.getFirst()).contains("(operacion_padre_id)");
    }
}
