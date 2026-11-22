package ar.edu.utn.frc.siga.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    @Test
    @DisplayName("responde 401 problem+json con detail, instance, status y title, e instance es la ruta pedida")
    void responde401ConLaFormaDelHandlerGlobal() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/buildings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint().commence(request, response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = JsonMapper.builder().build().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        List<String> keys = new ArrayList<>();
        body.propertyNames().forEach(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("detail", "instance", "status", "title");
        assertThat(body.get("instance").asString()).isEqualTo("/api/v1/buildings");
        assertThat(body.get("status").asInt()).isEqualTo(401);
    }
}
