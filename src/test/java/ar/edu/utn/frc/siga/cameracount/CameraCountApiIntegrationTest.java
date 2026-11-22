package ar.edu.utn.frc.siga.cameracount;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Conteo por cámaras API (integración, conteo deshabilitado en el perfil integration)")
class CameraCountApiIntegrationTest extends AbstractIntegrationTest {

    private static final String COUNTS = "/v1/camera-counts";
    private static final String HEALTH = "/v1/camera-counts/health";
    private static final String VALID_BODY =
            "{\"rtspUrl\":\"rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101\",\"samples\":3}";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private static MockHttpServletRequestBuilder postCount(String body) {
        return post(COUNTS).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockMvc anonymous() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private MockMvc consulta() {
        return mockMvcAs("camera-count-consulta@frc.utn.edu.ar", SystemRole.CONSULTA);
    }

    private MockMvc auxiliar() {
        return mockMvcAsAuxiliar("camera-count-auxiliar@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("sin token: POST y GET /health responden 401")
    void withoutTokenBothEndpointsAreUnauthorized() throws Exception {
        anonymous().perform(postCount(VALID_BODY)).andExpect(status().isUnauthorized());
        anonymous().perform(get(HEALTH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CONSULTA no tiene CAMERA_COUNT_RUN: POST y GET /health responden 403")
    void consultaIsForbiddenOnBothEndpoints() throws Exception {
        consulta().perform(postCount(VALID_BODY)).andExpect(status().isForbidden());
        consulta().perform(get(HEALTH)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO de un edificio pasa la autorización: con el conteo deshabilitado, POST y GET /health responden 503")
    void auxiliarReachesTheServiceAndGetsDisabled() throws Exception {
        MockMvc auxiliar = auxiliar();

        auxiliar.perform(postCount(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("deshabilitado")))
                .andExpect(content().string(not(containsString("s3cr3t"))));
        auxiliar.perform(get(HEALTH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("deshabilitado")));
    }

    @Test
    @DisplayName("SUBSECRETARIA: con el conteo deshabilitado, POST y GET /health responden 503")
    void subsecretariaGetsDisabled() throws Exception {
        mockMvc.perform(postCount(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("deshabilitado")))
                .andExpect(content().string(not(containsString("s3cr3t"))));
        mockMvc.perform(get(HEALTH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("deshabilitado")));
    }

    @Test
    @DisplayName("AUXILIAR_AULICO con samples fuera de rango: 400 con el error de samples, no 503")
    void auxiliarWithInvalidSamplesGetsBadRequest() throws Exception {
        auxiliar().perform(postCount(VALID_BODY.replace("\"samples\":3", "\"samples\":11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.samples").isNotEmpty())
                .andExpect(content().string(not(containsString("s3cr3t"))));
    }

    @Test
    @DisplayName("AUXILIAR_AULICO con una URL http en vez de rtsp: 400 con el error de rtspUrl y sin la URL en el body")
    void auxiliarWithNonRtspUrlGetsBadRequest() throws Exception {
        auxiliar().perform(postCount("{\"rtspUrl\":\"http://admin:s3cr3t@172.16.1.10/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rtspUrl").isNotEmpty())
                .andExpect(content().string(not(containsString("s3cr3t"))));
    }
}
