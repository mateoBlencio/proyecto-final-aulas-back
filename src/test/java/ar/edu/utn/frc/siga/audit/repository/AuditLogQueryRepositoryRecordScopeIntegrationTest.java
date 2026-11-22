package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code findChanges} / {@code countChanges} with a record scope against real Postgres. Rows are inserted with
 * {@code JdbcTemplate} into the {@code _aud} tables of two entities whose ids can share the same number
 * (Setting has a text id, Occurrence a numeric one).
 */
@DisplayName("AuditLogQueryRepository con ChangeScope.ofRecord (integración)")
class AuditLogQueryRepositoryRecordScopeIntegrationTest extends AbstractIntegrationTest {

    private static final long SHARED_NUMBER = 987_654_321L;

    @Autowired
    private AuditLogQueryRepository repository;
    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Integer> insertedRevisions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        insertedRevisions.forEach(rev -> {
            jdbcTemplate.update("DELETE FROM revinfo_resumen WHERE rev = ?", rev);
            jdbcTemplate.update("DELETE FROM configuracion_aud WHERE rev = ?", rev);
            jdbcTemplate.update("DELETE FROM ocurrencia_aud WHERE rev = ?", rev);
            jdbcTemplate.update("DELETE FROM revinfo WHERE rev = ?", rev);
        });
        insertedRevisions.clear();
    }

    private AuditedEntity entity(String label) {
        return registry.byLabel(label).orElseThrow();
    }

    private int newRevision() {
        Integer rev = jdbcTemplate.queryForObject(
                "INSERT INTO revinfo (fecha_revision, usuario, tipo_actor, descripcion) "
                        + "VALUES (now(), 'record-scope-test', 'HUMAN', 'Record scope') RETURNING rev", Integer.class);
        insertedRevisions.add(rev);
        return rev;
    }

    private int settingRevision(String key) {
        int rev = newRevision();
        jdbcTemplate.update("INSERT INTO configuracion_aud (clave, rev, revtype, valor) VALUES (?, ?, 1, 'x')", key, rev);
        jdbcTemplate.update("INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, 'configuracion_aud', 1, 1)", rev);
        return rev;
    }

    private int occurrenceRevision(long id) {
        int rev = newRevision();
        jdbcTemplate.update("INSERT INTO ocurrencia_aud (id_ocurrencia, rev, revtype, estado) VALUES (?, ?, 1, 'NEEDS_ROOM')",
                id, rev);
        jdbcTemplate.update("INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, 'ocurrencia_aud', 1, 1)", rev);
        return rev;
    }

    private static AuditLogCriteria onlyTarget(AuditedEntity entity) {
        return new AuditLogCriteria(null, null, null, null, List.of(entity), null, null);
    }

    private List<AuditChangeRow> rows(AuditedEntity entity, String recordId) {
        return repository.findChanges(onlyTarget(entity), ChangeScope.ofRecord(entity, recordId), PageRequest.of(0, 50));
    }

    @Test
    @DisplayName("con un id de texto devuelve solo las revisiones de esa clave, por revisión descendente, y el count coincide")
    void textId_filtersByIdColumn() {
        AuditedEntity setting = entity("Configuración");
        String key = "scope-test-" + UUID.randomUUID();
        int first = settingRevision(key);
        settingRevision("scope-test-other-" + UUID.randomUUID());
        int second = settingRevision(key);

        List<AuditChangeRow> rows = rows(setting, key);

        assertThat(rows).extracting(row -> row.metadata().revision()).containsExactly(second, first);
        assertThat(rows).extracting(row -> row.metadata().recordId()).containsOnly(key);
        assertThat(repository.countChanges(onlyTarget(setting), ChangeScope.ofRecord(setting, key))).isEqualTo(2);
    }

    @Test
    @DisplayName("con un id numérico no mezcla otro id de la misma entidad ni una configuración con el mismo número")
    void numericId_doesNotMixOtherIdsNorOtherEntities() {
        AuditedEntity occurrence = entity("Ocurrencia");
        AuditedEntity setting = entity("Configuración");
        int occurrenceFirst = occurrenceRevision(SHARED_NUMBER);
        int occurrenceSecond = occurrenceRevision(SHARED_NUMBER);
        occurrenceRevision(SHARED_NUMBER + 1);
        int settingRev = settingRevision(String.valueOf(SHARED_NUMBER));

        List<AuditChangeRow> occurrenceRows = rows(occurrence, String.valueOf(SHARED_NUMBER));
        List<AuditChangeRow> settingRows = rows(setting, String.valueOf(SHARED_NUMBER));

        assertThat(occurrenceRows).extracting(row -> row.metadata().revision())
                .containsExactly(occurrenceSecond, occurrenceFirst);
        assertThat(occurrenceRows).extracting(row -> row.entity().label()).containsOnly("Ocurrencia");
        assertThat(occurrenceRows).extracting(row -> row.metadata().recordId()).containsOnly(String.valueOf(SHARED_NUMBER));
        assertThat(repository.countChanges(onlyTarget(occurrence), ChangeScope.ofRecord(occurrence, String.valueOf(SHARED_NUMBER))))
                .isEqualTo(2);
        assertThat(settingRows).extracting(row -> row.metadata().revision()).containsExactly(settingRev);
        assertThat(settingRows).extracting(row -> row.entity().label()).containsOnly("Configuración");
        assertThat(repository.countChanges(onlyTarget(setting), ChangeScope.ofRecord(setting, String.valueOf(SHARED_NUMBER))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("un id sin revisiones devuelve 0 filas y count 0")
    void unknownId_returnsNothing() {
        AuditedEntity occurrence = entity("Ocurrencia");
        occurrenceRevision(SHARED_NUMBER);

        assertThat(rows(occurrence, String.valueOf(SHARED_NUMBER + 5))).isEmpty();
        assertThat(repository.countChanges(onlyTarget(occurrence),
                ChangeScope.ofRecord(occurrence, String.valueOf(SHARED_NUMBER + 5)))).isZero();
    }

    @Test
    @DisplayName("la paginación recorta el resultado del registro y el count sigue siendo el total")
    void pagination_cutsTheRecordResultOnly() {
        AuditedEntity occurrence = entity("Ocurrencia");
        occurrenceRevision(SHARED_NUMBER);
        int newest = occurrenceRevision(SHARED_NUMBER);
        occurrenceRevision(SHARED_NUMBER + 1);

        List<AuditChangeRow> firstPage = repository.findChanges(onlyTarget(occurrence),
                ChangeScope.ofRecord(occurrence, String.valueOf(SHARED_NUMBER)), PageRequest.of(0, 1));

        assertThat(firstPage).extracting(row -> row.metadata().revision()).containsExactly(newest);
        assertThat(repository.countChanges(onlyTarget(occurrence),
                ChangeScope.ofRecord(occurrence, String.valueOf(SHARED_NUMBER)))).isEqualTo(2);
    }

    @Test
    @DisplayName("con un criteria de varios targets y scope de registro devuelve solo filas de esa entidad y no falla")
    void multipleTargets_onlyScopedEntityRows() {
        AuditedEntity occurrence = entity("Ocurrencia");
        AuditedEntity setting = entity("Configuración");
        AuditedEntity allocation = entity("Asignación");
        int occurrenceRev = occurrenceRevision(SHARED_NUMBER);
        occurrenceRevision(SHARED_NUMBER + 1);
        settingRevision(String.valueOf(SHARED_NUMBER));
        AuditLogCriteria criteria = new AuditLogCriteria(null, null, null, null,
                List.of(setting, occurrence, allocation), null, null);
        ChangeScope scope = ChangeScope.ofRecord(occurrence, String.valueOf(SHARED_NUMBER));

        List<AuditChangeRow> rows = repository.findChanges(criteria, scope, PageRequest.of(0, 50));

        assertThat(rows).extracting(row -> row.metadata().revision()).containsExactly(occurrenceRev);
        assertThat(rows).extracting(row -> row.entity().label()).containsOnly("Ocurrencia");
        assertThat(repository.countChanges(criteria, scope)).isEqualTo(1);
    }

    @Test
    @DisplayName("todos los idType del registry son soportados por ofRecord: el id 'abc' no lanza IllegalStateException")
    void everyRegistryIdTypeIsSupported() {
        assertThat(registry.all()).allSatisfy(entity -> {
            try {
                ChangeScope.ofRecord(entity, "abc");
            } catch (ar.edu.utn.frc.siga.common.exception.InvalidSelectionException expected) {
                // not convertible is the expected 400 for numeric ids
            }
        });
    }
}
