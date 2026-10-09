package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

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
                        "Setting", "AuditArchiveRun");
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

    @Test
    @DisplayName("Occurrence audita event, date y status en ocurrencia_aud con sus columnas")
    void occurrenceHasEventDateAndStatusColumns() {
        assertThat(columnsOf("Occurrence")).contains(
                tuple("ocurrencia_aud", "id_evento_academico", "event"),
                tuple("ocurrencia_aud", "fecha", "date"),
                tuple("ocurrencia_aud", "estado", "status"));
    }

    @Test
    @DisplayName("AcademicEvent incluye las columnas de las tablas de subclase JOINED")
    void academicEventIncludesSubclassColumns() {
        assertThat(columnsOf("AcademicEvent")).contains(
                tuple("evento_recurrente_aud", "dia_semana", "dayOfWeek"),
                tuple("evento_unico_aud", "fecha", "date"));
    }

    @Test
    @DisplayName("idType es String para Setting y Long para Allocation")
    void idTypeMatchesTheEntityId() {
        assertThat(entity("Setting").idType()).isEqualTo(String.class);
        assertThat(entity("Allocation").idType()).isEqualTo(Long.class);
    }

    @Test
    @DisplayName("User no incluye passwordHash ni ninguna columna password_hash")
    void userExcludesPasswordHash() {
        assertThat(entity("User").columns()).extracting(AuditedColumn::property).isNotEmpty()
                .doesNotContain("passwordHash");
        assertThat(entity("User").columns()).extracting(AuditedColumn::column).doesNotContain("password_hash");
    }

    @Test
    @DisplayName("ninguna propiedad termina en _id: las relaciones salen con el nombre de la propiedad Java")
    void noPropertyEndsWithIdSuffix() {
        assertThat(registry.all()).allSatisfy(entity ->
                assertThat(entity.columns()).extracting(AuditedColumn::property)
                        .as("propiedades de %s", entity.jpaName())
                        .noneMatch(property -> property.endsWith("_id")));
    }

    @Test
    @DisplayName("el sufijo _id solo se quita en relaciones to-one: event, user e item salen sin él y los ids planos quedan intactos")
    void idSuffixIsStrippedOnlyForToOneRelations() {
        assertThat(columnsOf("Occurrence")).extracting(t -> t.toList().get(2)).contains("event");
        assertThat(columnsOf("RoleAssignment")).extracting(t -> t.toList().get(2)).contains("user");
        assertThat(columnsOf("RoomPreference")).extracting(t -> t.toList().get(2)).contains("item", "classroomId");
        assertThat(columnsOf("RoomRequestItemAllocation")).extracting(t -> t.toList().get(2))
                .contains("item", "occurrenceId", "classroomId");
        assertThat(columnsOf("Allocation")).extracting(t -> t.toList().get(2))
                .contains("occurrenceId", "classroomId")
                .doesNotContain("occurrence", "classroom");
    }

    @Test
    @DisplayName("ninguna entidad expone las columnas técnicas de Envers ni su id como campo")
    void columnsExcludeEnversTechnicalColumns() {
        assertThat(registry.all()).allSatisfy(entity ->
                assertThat(entity.columns()).extracting(AuditedColumn::column)
                        .as("columnas de %s", entity.jpaName())
                        .doesNotContain("rev", "revtype", entity.idColumn()));
    }

    @Test
    @DisplayName("SELECT <columnas> FROM <tabla> LIMIT 0 no lanza en ninguna entidad")
    void everyDeclaredColumnIsQueryable() {
        assertThat(registry.all()).allSatisfy(entity -> {
            Set<String> tables = entity.columns().stream().map(AuditedColumn::auditTable)
                    .collect(Collectors.toSet());
            assertThat(tables).isNotEmpty();
            for (String table : tables) {
                String columns = entity.columns().stream().filter(c -> c.auditTable().equals(table))
                        .map(AuditedColumn::column).collect(Collectors.joining(", "));
                assertThatCode(() -> jdbcTemplate.queryForList("SELECT " + columns + " FROM " + table + " LIMIT 0"))
                        .as("columnas de %s en %s", entity.jpaName(), table)
                        .doesNotThrowAnyException();
            }
        });
    }

    private AuditedEntity entity(String jpaName) {
        return registry.all().stream().filter(e -> e.jpaName().equals(jpaName)).findFirst().orElseThrow();
    }

    // Table names may include a schema ("public.x_aud"): only the last segment is compared.
    private List<org.assertj.core.groups.Tuple> columnsOf(String jpaName) {
        return entity(jpaName).columns().stream()
                .map(c -> tuple(c.auditTable().substring(c.auditTable().lastIndexOf('.') + 1), c.column(), c.property()))
                .toList();
    }

    // getTableName() may include a schema ("public.x_aud"): only the last segment is compared.
    private List<String> tableAndColumn(String jpaName) {
        AuditedEntity entity = registry.all().stream()
                .filter(e -> e.jpaName().equals(jpaName)).findFirst().orElseThrow();
        String table = entity.auditTable();
        return List.of(table.substring(table.lastIndexOf('.') + 1), entity.idColumn());
    }
}
