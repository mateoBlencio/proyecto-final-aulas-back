package ar.edu.utn.frc.siga.common.web;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los 401 y 403 que escriben a mano los handlers de seguridad tienen que salir en UTF-8 por el socket.
 * MockMvc no lo detecta: MockHttpServletResponse codifica el Writer con el charset que le pongan, en cambio
 * Tomcat usa ISO-8859-1 cuando el Content-Type no declara charset. Por eso este test usa un servidor real
 * (RANDOM_PORT) y lee los bytes crudos de la respuesta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Charset de los ProblemDetail de 401 y 403 sobre HTTP real (integracion)")
class ProblemDetailCharsetIntegrationTest extends AbstractIntegrationTest {

    private static final String CONSULTA_EMAIL = "consulta-charset@frc.utn.edu.ar";
    private static final String FORBIDDEN_DETAIL = "No tenés permiso para acceder a este recurso.";
    private static final byte[] UTF8_E_ACUTE = {(byte) 0xC3, (byte) 0xA9};
    private static final byte LATIN1_E_ACUTE = (byte) 0xE9;

    @LocalServerPort
    private int port;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("un 403 de un usuario CONSULTA llega con la e con tilde en UTF-8 (C3 A9), sin E9 y sin ISO-8859-1")
    void forbiddenBodyIsUtf8OnTheWire() throws Exception {
        mockMvcAs(CONSULTA_EMAIL, SystemRole.CONSULTA);
        String token = jwtService.generateAccessToken(CONSULTA_EMAIL);

        HttpResponse<byte[]> response = send(request("/api/v1/buildings/999999999/activation")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(403);
        byte[] body = response.body();
        assertThat(indexOf(body, UTF8_E_ACUTE)).as("la e de 'tenés' como C3 A9").isGreaterThanOrEqualTo(0);
        assertThat(contains(body, LATIN1_E_ACUTE)).as("ningún byte E9 (e en ISO-8859-1)").isFalse();
        assertThat(contentType(response)).startsWith("application/problem+json").doesNotContainIgnoringCase("ISO-8859-1");
        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("title").asString()).isEqualTo("Forbidden");
        assertThat(json.get("detail").asString()).isEqualTo(FORBIDDEN_DETAIL);
        assertThat(json.get("instance").asString()).isEqualTo("/api/v1/buildings/999999999/activation");
    }

    @Test
    @DisplayName("un 401 sin token llega como application/problem+json sin ISO-8859-1 y con el JSON esperado")
    void unauthorizedBodyIsUtf8OnTheWire() throws Exception {
        HttpResponse<byte[]> response = send(request("/api/v1/buildings").GET());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(contentType(response)).startsWith("application/problem+json").doesNotContainIgnoringCase("ISO-8859-1");
        byte[] body = response.body();
        assertThat(contains(body, LATIN1_E_ACUTE)).as("ningún byte E9").isFalse();
        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("title").asString()).isEqualTo("Unauthorized");
        assertThat(json.get("detail").asString()).isEqualTo("Authentication is required to access this resource");
        assertThat(json.get("instance").asString()).isEqualTo("/api/v1/buildings");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private static HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String contentType(HttpResponse<byte[]> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    private static boolean contains(byte[] haystack, byte needle) {
        for (byte b : haystack) {
            if (b == needle) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return i;
            }
        }
        return -1;
    }
}
