package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guarda de drift: fija el conjunto de entidades {@code @Audited} descubierto vía metamodelo.
 * Si se agrega o quita un {@code @Audited} sin actualizar esta expectativa (y el mapa de
 * etiquetas de {@link AuditedEntityRegistry}), este test falla.
 */
@DisplayName("AuditedEntityRegistry (drift guard)")
class AuditedEntityRegistryTest extends AbstractIntegrationTest {

    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("descubre exactamente los tipos raíz auditados, colapsando la herencia")
    void discoversExactlyTheExpectedRootAuditedTypes() {
        assertThat(registry.all()).extracting(AuditedEntity::jpaName)
                .containsExactlyInAnyOrder(
                        "Allocation", "User", "RoleAssignment", "AcademicEvent", "Occurrence",
                        "RoomRequest", "RoomRequestItem", "RoomPreference", "RoomRequestItemAllocation",
                        "Setting");
    }

    @Test
    @DisplayName("no expone subtipos cuya superclase ya es auditada")
    void doesNotExposeAuditedSubtypes() {
        assertThat(registry.all()).extracting(AuditedEntity::jpaName)
                .doesNotContain("RecurringEvent", "UniqueEvent");
    }

    @Test
    @DisplayName("cada tipo tiene una etiqueta de dominio propia")
    void everyTypeHasADomainLabel() {
        assertThat(registry.all()).allSatisfy(entity -> {
            assertThat(entity.label()).isNotBlank();
            assertThat(entity.label()).isNotEqualTo(entity.jpaName());
        });
    }

    @Test
    @DisplayName("Allocation, Setting y AcademicEvent apuntan a su tabla _aud y su columna id")
    void resolvesAuditTableAndIdColumn() {
        assertThat(tableAndColumn("Allocation")).containsExactly("asignacion_aula_aud", "id_asignacion");
        assertThat(tableAndColumn("Setting")).containsExactly("configuracion_aud", "clave");
        assertThat(tableAndColumn("AcademicEvent")).containsExactly("evento_academico_aud", "id_evento_academico");
    }

    @Test
    @DisplayName("cada entidad del registry tiene una tabla _aud con columnas id, rev y revtype consultables")
    void everyEntityAuditTableIsQueryable() {
        assertThat(registry.all()).allSatisfy(entity ->
                assertThatCode(() -> jdbcTemplate.queryForList(
                        "SELECT " + entity.idColumn() + ", rev, revtype FROM " + entity.auditTable() + " LIMIT 0"))
                        .as("consulta sobre %s", entity.auditTable())
                        .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("indexOf devuelve la posición en all()")
    void indexOfMatchesPosition() {
        for (int i = 0; i < registry.all().size(); i++) {
            assertThat(registry.indexOf(registry.all().get(i))).isEqualTo(i);
        }
    }

    // getTableName() may include a schema ("public.x_aud"): only the last segment is compared.
    private List<String> tableAndColumn(String jpaName) {
        AuditedEntity entity = registry.all().stream()
                .filter(e -> e.jpaName().equals(jpaName)).findFirst().orElseThrow();
        String table = entity.auditTable();
        return List.of(table.substring(table.lastIndexOf('.') + 1), entity.idColumn());
    }
}
