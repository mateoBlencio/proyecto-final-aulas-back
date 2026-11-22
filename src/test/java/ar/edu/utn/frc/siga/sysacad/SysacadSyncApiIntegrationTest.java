package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sync de SysAcad API (integración, flag encendido)")
@TestPropertySource(properties = "siga.sysacad.enabled=true")
class SysacadSyncApiIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Test
    @DisplayName("POST /v1/sysacad/sync con todas las vistas vacías: 207, cada vista con su código de error y sin detalles internos; el GET devuelve lo persistido")
    void resyncAll_withEmptyViews_returnsPartialFailureWithErrorCodes() throws Exception {
        when(snapshotFactory.newSnapshot()).thenReturn(mock(SysacadCatalogReader.class));

        mockMvc.perform(post("/v1/sysacad/sync"))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$[?(@.view=='EDIFICIOS')].lastError", contains(startsWith("EMPTY_VIEW:"))))
                .andExpect(jsonPath("$[?(@.view=='AULAS')].lastError", contains(startsWith("DEPENDENCY_FAILED:"))))
                .andExpect(jsonPath("$[?(@.view=='ASIGNACIONES')].lastError", contains(startsWith("DEPENDENCY_FAILED:"))))
                .andExpect(jsonPath("$[*].lastError", not(org.hamcrest.Matchers.hasItem(containsString("Exception")))));

        mockMvc.perform(get("/v1/sysacad/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[?(@.view=='EDIFICIOS')].lastError", contains(startsWith("EMPTY_VIEW:"))));
    }

    @Test
    @DisplayName("POST /v1/sysacad/sync con SysAcad inaccesible en todas las vistas: 503 y error CONNECTIVITY sin la causa interna")
    void resyncAll_withSysacadDown_returnsServiceUnavailable() throws Exception {
        SysacadCatalogReader down = mock(SysacadCatalogReader.class, invocation -> {
            throw new SysacadUnavailableException("connect to 10.1.2.3:443 failed", null);
        });
        when(snapshotFactory.newSnapshot()).thenReturn(down);

        mockMvc.perform(post("/v1/sysacad/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$[?(@.view=='EDIFICIOS')].lastError", contains(startsWith("CONNECTIVITY:"))))
                .andExpect(jsonPath("$[*].lastError", not(org.hamcrest.Matchers.hasItem(containsString("10.1.2.3")))));
    }

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
        return mockMvcAsAuxiliar("auxiliar@frc.utn.edu.ar");
    }
}
