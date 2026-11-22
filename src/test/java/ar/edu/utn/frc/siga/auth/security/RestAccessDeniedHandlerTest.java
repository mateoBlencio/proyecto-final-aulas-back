package ar.edu.utn.frc.siga.auth.security;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    @Test
    @DisplayName("responde 403 con el detalle en español")
    void responde403EnEspanol() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler().handle(new MockHttpServletRequest("GET", "/api/v1/x"), response,
                new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("No tenés permiso para acceder a este recurso.")
                .contains("\"instance\":\"/api/v1/x\"")
                .doesNotContain("\"type\"")
                .doesNotContain("null");
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }
}
