package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@code @AuditOperation} with {@code subject} ends with the names of the records it changed in its description
 * ("{base}: A, B y N más"), through the real aspect, the audit tables and the label providers. The test operation
 * writes settings, whose labels are the Spanish names of the keys.
 */
@DisplayName("Resumen de una operación auditada con subject (integración)")
class AuditOperationSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "Prueba de resumen";

    /** Alphabetical (Spanish) order: "Máximo de aulas...", "Máximo de computadoras...", "Máximo de inscriptos...". */
    private static final SettingKey AULAS = SettingKey.ROOM_REQUESTS_MAX_CLASSROOM_COUNT;
    private static final SettingKey COMPUTADORAS = SettingKey.ROOM_REQUESTS_MAX_COMPUTER_COUNT;
    private static final SettingKey INSCRIPTOS = SettingKey.ROOM_REQUESTS_MAX_ESTIMATED;
    /** "Minutos..." sorts after "Máximo..." in Spanish but before it by raw character code. */
    private static final SettingKey MINUTOS = SettingKey.ALLOCATION_MAX_OVERLAP_MINUTES;

    /** Test bean: audited methods that write settings, each write in its own committed transaction. */
    public static class SettingsWriter {

        private final SettingsStore settingsStore;
        private final PlatformTransactionManager transactionManager;

        SettingsWriter(SettingsStore settingsStore, PlatformTransactionManager transactionManager) {
            this.settingsStore = settingsStore;
            this.transactionManager = transactionManager;
        }

        @AuditOperation(value = BASE, subject = "Setting")
        public void writeWithSubject(List<SettingKey> keys) {
            write(keys);
        }

        @AuditOperation(value = BASE, subject = "EntidadQueNoExiste")
        public void writeWithUnknownSubject(List<SettingKey> keys) {
            write(keys);
        }

        @AuditOperation(BASE)
        public void writeWithoutSubject(List<SettingKey> keys) {
            write(keys);
        }

        private void write(List<SettingKey> keys) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            for (SettingKey key : keys) {
                tx.executeWithoutResult(s ->
                        settingsStore.write(key, String.valueOf(Long.parseLong(settingsStore.getRaw(key)) + 1)));
            }
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        SettingsWriter settingsWriter(SettingsStore settingsStore, PlatformTransactionManager transactionManager) {
            return new SettingsWriter(settingsStore, transactionManager);
        }
    }

    @Autowired
    private SettingsWriter writer;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Map<SettingKey, String> originals = new LinkedHashMap<>();

    @AfterEach
    void restoreSettings() {
        if (!originals.isEmpty()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                    originals.forEach(settingsStore::write));
            originals.clear();
        }
    }

    private int maxRevision() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Integer.class);
    }

    private void remember(SettingKey... keys) {
        for (SettingKey key : keys) {
            originals.put(key, settingsStore.getRaw(key));
        }
    }

    /** Distinct descriptions of the revisions of the operations created after {@code revision}. */
    private List<String> descriptionsAfter(int revision) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT descripcion FROM revinfo WHERE rev > ? AND operacion_id IS NOT NULL",
                String.class, revision);
    }

    @Test
    @DisplayName("un registro: 'base: nombre'")
    void oneRecord_namesIt() {
        remember(AULAS);
        int before = maxRevision();

        writer.writeWithSubject(List.of(AULAS));

        assertThat(descriptionsAfter(before)).containsExactly(BASE + ": " + AULAS.getLabel());
    }

    @Test
    @DisplayName("tres registros se listan completos, ordenados alfabéticamente y todas las revisiones quedan con el mismo texto")
    void threeRecords_listedInFull() {
        remember(INSCRIPTOS, AULAS, COMPUTADORAS);
        int before = maxRevision();

        writer.writeWithSubject(List.of(INSCRIPTOS, AULAS, COMPUTADORAS));

        assertThat(descriptionsAfter(before)).containsExactly(BASE + ": " + AULAS.getLabel() + ", "
                + COMPUTADORAS.getLabel() + " y " + INSCRIPTOS.getLabel());
    }

    @Test
    @DisplayName("cuatro registros: dos nombres y 'y 2 más', con el orden del español (Máximo antes que Minutos)")
    void fourRecords_twoNamesAndMore() {
        remember(MINUTOS, INSCRIPTOS, AULAS, COMPUTADORAS);
        int before = maxRevision();

        writer.writeWithSubject(List.of(MINUTOS, INSCRIPTOS, AULAS, COMPUTADORAS));

        assertThat(descriptionsAfter(before)).containsExactly(BASE + ": " + AULAS.getLabel() + ", "
                + COMPUTADORAS.getLabel() + " y 2 más");
    }

    @Test
    @DisplayName("un subject que no es una entidad auditada deja la descripción base")
    void unknownSubject_keepsBase() {
        remember(AULAS);
        int before = maxRevision();

        writer.writeWithUnknownSubject(List.of(AULAS));

        assertThat(descriptionsAfter(before)).containsExactly(BASE);
    }

    @Test
    @DisplayName("sin subject la descripción queda como la del annotation")
    void noSubject_keepsBase() {
        remember(AULAS);
        int before = maxRevision();

        writer.writeWithoutSubject(List.of(AULAS));

        assertThat(descriptionsAfter(before)).containsExactly(BASE);
    }
}
