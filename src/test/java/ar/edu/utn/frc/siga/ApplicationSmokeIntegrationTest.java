package ar.edu.utn.frc.siga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test: verifica que el contexto completo de la aplicación levanta contra
 * un Postgres real (Testcontainers) y que un endpoint básico responde.
 */
class ApplicationSmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("El contexto de la aplicación levanta correctamente")
    void contextLoads() {
    }

    @Test
    @DisplayName("GET /v1/buildings responde 200")
    void getBuildings_returnsOk() throws Exception {
        mockMvc.perform(get("/v1/buildings"))
                .andExpect(status().isOk());
    }
}
