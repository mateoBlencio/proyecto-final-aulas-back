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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A description set after Envers already stamped revisions (writes in separate transactions inside
 * one {@code @AuditOperation}) must reach every revision of the operation, and only that operation.
 */
@DisplayName("Descripción tardía de una operación auditada (integración)")
class AuditLateDescriptionIntegrationTest extends AbstractIntegrationTest {

    private static final String INITIAL = "inicial-descripcion-tardia";
    private static final SettingKey FIRST = SettingKey.PREVIEW_TTL_MINUTES;
    private static final SettingKey SECOND = SettingKey.PREVIEW_DEFAULT_TIME_LIMIT_SECONDS;

    /** Test bean: one audited method that writes two settings in two separate committed transactions. */
    public static class LateDescriber {

        private final SettingsStore settingsStore;
        private final PlatformTransactionManager transactionManager;

        LateDescriber(SettingsStore settingsStore, PlatformTransactionManager transactionManager) {
            this.settingsStore = settingsStore;
            this.transactionManager = transactionManager;
        }

        @AuditOperation(INITIAL)
        public void writeTwiceThenDescribe(String finalText) {
            writeInOwnTransactions();
            AuditOperations.describe(finalText);
        }

        @AuditOperation(INITIAL)
        public void writeTwiceDescribeThenFail(String finalText) {
            writeInOwnTransactions();
            AuditOperations.describe(finalText);
            throw new IllegalStateException("boom");
        }

        private void writeInOwnTransactions() {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(s -> bump(FIRST));
            tx.executeWithoutResult(s -> bump(SECOND));
        }

        private void bump(SettingKey key) {
            settingsStore.write(key, String.valueOf(Long.parseLong(settingsStore.getRaw(key)) + 1));
        }
    }

    /** Plain transactional caller (no operation of its own) that rolls back after the audited call. */
    public static class RollingBackCaller {

        private final LateDescriber describer;

        RollingBackCaller(LateDescriber describer) {
            this.describer = describer;
        }

        @Transactional
        public void callThenRollback(String finalText) {
            describer.writeTwiceThenDescribe(finalText);
            throw new IllegalStateException("rollback del llamador");
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        LateDescriber lateDescriber(SettingsStore settingsStore, PlatformTransactionManager transactionManager) {
            return new LateDescriber(settingsStore, transactionManager);
        }

        @Bean
        RollingBackCaller rollingBackCaller(LateDescriber describer) {
            return new RollingBackCaller(describer);
        }
    }

    @Autowired
    private LateDescriber describer;
    @Autowired
    private RollingBackCaller rollingBackCaller;
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

    private void rememberOriginals() {
        originals.put(FIRST, settingsStore.getRaw(FIRST));
        originals.put(SECOND, settingsStore.getRaw(SECOND));
    }

    /** Descriptions of the revisions with an operation created after {@code revision}. */
    private List<String> operationDescriptionsAfter(int revision) {
        return jdbcTemplate.queryForList(
                "SELECT descripcion FROM revinfo WHERE rev > ? AND operacion_id IS NOT NULL ORDER BY rev",
                String.class, revision);
    }

    private String uniqueText() {
        return "Descripción final " + System.nanoTime();
    }

    @Test
    @DisplayName("describe después de dos transacciones commiteadas deja las dos revisiones con la descripción final")
    void lateDescribeRewritesEveryRevisionOfTheOperation() {
        rememberOriginals();
        int before = maxRevision();
        String finalText = uniqueText();

        describer.writeTwiceThenDescribe(finalText);

        assertThat(operationDescriptionsAfter(before)).containsExactly(finalText, finalText);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT operacion_id) FROM revinfo WHERE descripcion = ?", Integer.class, finalText))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("las revisiones de otras operaciones conservan su descripción")
    void otherOperationsKeepTheirDescription() throws Exception {
        rememberOriginals();
        mockMvc.perform(put("/v1/settings/{key}", "events.hours.end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"22:11\"}"))
                .andExpect(status().isOk());
        Map<String, Object> other = jdbcTemplate.queryForMap(
                "SELECT operacion_id, descripcion FROM revinfo WHERE operacion_id IS NOT NULL ORDER BY rev DESC LIMIT 1");
        assertThat(other.get("descripcion")).isEqualTo("Modificación de configuración");

        describer.writeTwiceThenDescribe(uniqueText());

        assertThat(jdbcTemplate.queryForList(
                "SELECT descripcion FROM revinfo WHERE operacion_id = ?", String.class, other.get("operacion_id")))
                .isNotEmpty()
                .containsOnly("Modificación de configuración");
    }

    @Test
    @DisplayName("si el método falla después del describe, las revisiones ya commiteadas toman la descripción final")
    void failingMethodAppliesTheFinalDescriptionToCommittedRevisions() {
        rememberOriginals();
        int before = maxRevision();
        String finalText = uniqueText();

        assertThatThrownBy(() -> describer.writeTwiceDescribeThenFail(finalText))
                .isInstanceOf(IllegalStateException.class);

        assertThat(operationDescriptionsAfter(before)).containsExactly(finalText, finalText);
    }

    @Test
    @DisplayName("un llamador @Transactional que hace rollback no deshace la descripción final de las revisiones ya commiteadas")
    void callerRollbackKeepsFinalDescription() {
        rememberOriginals();
        int before = maxRevision();
        String finalText = uniqueText();

        assertThatThrownBy(() -> rollingBackCaller.callThenRollback(finalText))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback del llamador");

        assertThat(operationDescriptionsAfter(before)).containsExactly(finalText, finalText);
    }
}
