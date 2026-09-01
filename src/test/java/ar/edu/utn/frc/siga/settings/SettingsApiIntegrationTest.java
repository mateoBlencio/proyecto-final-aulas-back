package ar.edu.utn.frc.siga.settings;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.audit.RevisionReader;
import ar.edu.utn.frc.siga.audit.RevisionDto;
import ar.edu.utn.frc.siga.settings.config.SettingsCatalogProperties;
import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.repository.SettingRepository;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@DisplayName("Settings API (integración)")
class SettingsApiIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "integration-test@frc.utn.edu.ar";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private SettingRepository settingRepository;
    @Autowired
    private RevisionReader revisionReader;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SettingsCatalogProperties catalog;

    @BeforeEach
    void ensureSeeded() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (SettingKey key : SettingKey.values()) {
                if (!settingRepository.existsById(key.getKey())) {
                    settingRepository.save(new Setting(key.getKey(), catalog.defaultValue(key)));
                }
            }
        });
    }

    @Test
    @DisplayName("GET /v1/settings agrupa por categoría con metadata y valores sembrados")
    void findAll_groupsByCategoryWithSeededValues() throws Exception {
        mockMvc.perform(get("/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizer").isArray())
                .andExpect(jsonPath("$.preview").isArray())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[?(@.key=='events.hours.start')].value").value("08:00"))
                .andExpect(jsonPath("$.events[?(@.key=='events.hours.start')].type").value("TIME"))
                .andExpect(jsonPath("$.optimizer[?(@.key=='optimizer.weights.overcrowding')].min").value("0"))
                .andExpect(jsonPath("$.optimizer[?(@.key=='optimizer.weights.overcrowding')].max").value("1000000"));
    }

    @Test
    @DisplayName("PUT /v1/settings/{key} valida, persiste y el valor queda disponible al instante")
    void put_validValue_persistsAndRefreshes() throws Exception {
        putValue("optimizer.weights.overcrowding", "55555")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("55555"));

        assertThat(settingRepository.findById("optimizer.weights.overcrowding"))
                .get()
                .extracting(Setting::getValue)
                .isEqualTo("55555");

        mockMvc.perform(get("/v1/settings/{key}", "optimizer.weights.overcrowding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("55555"));
    }

    @Test
    @DisplayName("PUT /v1/settings/{key} fuera de las cotas responde 400 y no persiste")
    void put_outOfBounds_returns400() throws Exception {
        putValue("optimizer.solverSecondsSpentLimit", "99999")
                .andExpect(status().isBadRequest());

        assertThat(settingRepository.findById("optimizer.solverSecondsSpentLimit"))
                .get()
                .extracting(Setting::getValue)
                .isEqualTo("300");
    }

    @Test
    @DisplayName("PUT /v1/settings/{key} con clave inexistente responde 404")
    void put_unknownKey_returns404() throws Exception {
        putValue("optimizer.noExiste", "1").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /v1/settings batch que falla a mitad no persiste ninguno (rollback)")
    void putBatch_partialFailure_rollsBackAll() throws Exception {
        String body = """
                {"settings":[
                  {"key":"preview.ttlMinutes","value":"120"},
                  {"key":"preview.defaultTimeLimitSeconds","value":"99999"}
                ]}
                """;

        mockMvc.perform(put("/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(settingRepository.findById("preview.ttlMinutes"))
                .get().extracting(Setting::getValue).isEqualTo("30");
    }

    @Test
    @DisplayName("PUT /v1/settings/{key} genera una revisión de Envers con el usuario autenticado")
    void put_generatesAuditRevisionWithUser() throws Exception {
        putValue("events.hours.end", "22:30").andExpect(status().isOk());

        List<RevisionDto<String>> revisions = new TransactionTemplate(transactionManager).execute(tx ->
                revisionReader.read(Setting.class, "key", "events.hours.end", Setting::getValue));

        assertThat(revisions).isNotEmpty();
        RevisionDto<String> last = revisions.getLast();
        assertThat(last.user()).isEqualTo(USER);
        assertThat(last.snapshot()).isEqualTo("22:30");
    }

    @Test
    @DisplayName("Un AUXILIAR_AULICO no puede modificar configuración (403)")
    void put_asAuxiliar_isForbidden() throws Exception {
        String auxToken = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        MockMvc auxiliarMockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + auxToken))
                .build();

        auxiliarMockMvc.perform(put("/v1/settings/{key}", "optimizer.weights.overcrowding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"123\"}"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions putValue(String key, String value) throws Exception {
        return mockMvc.perform(put("/v1/settings/{key}", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PutBody(value))));
    }

    private record PutBody(String value) {
    }
}
