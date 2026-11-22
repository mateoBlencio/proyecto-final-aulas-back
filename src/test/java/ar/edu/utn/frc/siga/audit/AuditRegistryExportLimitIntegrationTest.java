package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Export limit of {@code GET /v1/audit/export}: needs its own context to lower {@code max-rows} to 1. */
@Import(IntegrationTestData.class)
@TestPropertySource(properties = "siga.audit.export.max-rows=1")
@DisplayName("Audit export: límite max-rows (integración)")
class AuditRegistryExportLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private String originalHoursEnd;

    @AfterEach
    void restoreHoursEnd() {
        if (originalHoursEnd != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                    settingsStore.write(SettingKey.EVENTS_HOURS_END, originalHoursEnd));
            originalHoursEnd = null;
        }
    }

    private void bumpSetting(String value) throws Exception {
        if (originalHoursEnd == null) {
            originalHoursEnd = settingsStore.getRaw(SettingKey.EVENTS_HOURS_END);
        }
        mockMvc.perform(put("/v1/settings/{key}", "events.hours.end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("con más entradas que max-rows responde 400 en español, JSON y sin Content-Disposition")
    void overLimitIsBadRequestWithoutAttachment() throws Exception {
        bumpSetting("21:31");
        bumpSetting("21:32");

        MvcResult result = mockMvc.perform(get("/v1/audit/export").param("entityType", "Configuración"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
        assertThat(result.getResponse().getContentType()).doesNotStartWith("text/csv");
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("el máximo para exportar es 1")
                .contains("Acotá el rango de fechas");
    }
}
