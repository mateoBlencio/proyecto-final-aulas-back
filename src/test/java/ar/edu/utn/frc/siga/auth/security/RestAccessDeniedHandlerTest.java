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

        new RestAccessDeniedHandler().handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("No tenés permiso para acceder a este recurso.");
    }
}
