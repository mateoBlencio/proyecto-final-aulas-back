package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@DisplayName("Sync de SysAcad API (integración, flag encendido)")
@TestPropertySource(properties = "siga.sysacad.enabled=true")
class SysacadSyncApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;

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
        String token = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        return webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }
}
