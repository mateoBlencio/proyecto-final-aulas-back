package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sync de SysAcad API (integración, flag apagado)")
class SysacadSyncDisabledIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Con siga.sysacad.enabled=false el endpoint de sync no existe (404)")
    void syncEndpoints_areNotRegistered() throws Exception {
        mockMvc.perform(post("/v1/sysacad/sync"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/sysacad/sync"))
                .andExpect(status().isNotFound());
    }
}
