package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The archive copies with {@code SELECT a.*}, so both schemas must keep the same columns in the same order.
 * These checks fail when a later migration alters only one side.
 */
@DisplayName("Esquema archivo vs public (integración)")
class AuditArchiveSchemaIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> MIRRORED_TABLES =
            List.of("revinfo", "revinfo_resumen", "ocurrencia_aud", "asignacion_aula_aud");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditedEntityRegistry registry;

    private List<Map<String, Object>> columns(String schema, String table) {
        return jdbc.queryForList("SELECT column_name, ordinal_position, data_type, character_maximum_length, "
                + "is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = ? "
                + "ORDER BY ordinal_position", schema, table);
    }

    @Test
    @DisplayName("las cuatro tablas de archivo tienen las mismas columnas, orden y tipos que las de public")
    void mirroredTablesHaveTheSameColumns() {
        for (String table : MIRRORED_TABLES) {
            List<Map<String, Object>> active = columns("public", table);

            assertThat(active).as("columnas de public.%s", table).isNotEmpty();
            assertThat(columns("archivo", table)).as("columnas de archivo.%s", table).isEqualTo(active);
        }
    }

    @Test
    @DisplayName("archivo no tiene más tablas que las cuatro espejadas")
    void archiveHasOnlyTheMirroredTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'archivo'", String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(MIRRORED_TABLES);
    }

    @Test
    @DisplayName("las tablas con FK a public.revinfo son exactamente las auditTable del registry más revinfo_resumen")
    void foreignKeysToRevinfoAreTheRegistryTablesPlusTheSummary() {
        Set<String> referencing = new TreeSet<>(jdbc.queryForList(
                "SELECT DISTINCT c.conrelid::regclass::text FROM pg_constraint c "
                        + "WHERE c.contype = 'f' AND c.confrelid = 'public.revinfo'::regclass", String.class));
        Set<String> expected = registry.all().stream().map(AuditedEntity::auditTable)
                .collect(Collectors.toCollection(TreeSet::new));
        expected.add("revinfo_resumen");

        assertThat(referencing).isEqualTo(expected);
    }

    @Test
    @DisplayName("el schema archivo no tiene constraints de clave foránea ni CHECK")
    void archiveHasNoForeignKeysNorChecks() {
        List<String> constraints = jdbc.queryForList(
                "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE n.nspname = 'archivo' AND c.contype IN ('f', 'c')", String.class);

        assertThat(constraints).isEmpty();
    }

    @Test
    @DisplayName("cada tabla de archivo conserva su clave primaria (la necesita ON CONFLICT)")
    void archiveTablesKeepTheirPrimaryKey() {
        for (String table : MIRRORED_TABLES) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype = 'p' "
                    + "AND conrelid = ('archivo.' || ?::text)::regclass", Integer.class, table))
                    .as("PK de archivo.%s", table).isEqualTo(1);
        }
    }
}
