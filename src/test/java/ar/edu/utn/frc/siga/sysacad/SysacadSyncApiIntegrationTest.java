package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sync de SysAcad API (integración, flag encendido)")
@TestPropertySource(properties = "siga.sysacad.enabled=true")
class SysacadSyncApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /v1/sysacad/sync devuelve el estado del sync a la Subsecretaría")
    void findState_asSubsecretaria_isOk() throws Exception {
        mockMvc.perform(get("/v1/sysacad/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Un AUXILIAR_AULICO no puede consultar el estado del sync (403)")
    void findState_asAuxiliar_isForbidden() throws Exception {
        auxiliarMockMvc().perform(get("/v1/sysacad/sync"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un AUXILIAR_AULICO no puede disparar el resync manual (403)")
    void resyncAll_asAuxiliar_isForbidden() throws Exception {
        auxiliarMockMvc().perform(post("/v1/sysacad/sync"))
                .andExpect(status().isForbidden());
    }

    private MockMvc auxiliarMockMvc() {
        return mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);
    }
}
