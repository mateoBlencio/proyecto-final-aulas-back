package ar.edu.utn.frc.siga.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemDetailResponseWriter")
class ProblemDetailResponseWriterTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("escribe exactamente detail, instance, status y title, igual que los errores del GlobalExceptionHandler")
    void writesTheSameShapeAsTheGlobalExceptionHandler() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/v1/buildings"), response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "Authentication is required to access this resource");

        JsonNode body = body(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("detail", "instance", "status", "title");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asString()).isEqualTo("Authentication is required to access this resource");
    }

    @Test
    @DisplayName("instance es el requestURI del pedido")
    void instanceIsTheRequestUri() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/v1/building-activity"), response, HttpStatus.FORBIDDEN,
                "Forbidden", "sin permiso");

        assertThat(body(response).get("instance").asString()).isEqualTo("/api/v1/building-activity");
    }

    @Test
    @DisplayName("no serializa type ni valores nulos ni properties")
    void omitsTypeNullsAndProperties() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/x"), response, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests", "Rate limit exceeded");

        JsonNode body = body(response);
        assertThat(body.has("type")).isFalse();
        assertThat(body.has("properties")).isFalse();
        body.forEach(value -> assertThat(value.isNull()).isFalse());
    }

    @Test
    @DisplayName("un detail nulo no deja una clave detail con null")
    void nullDetailLeavesNoKey() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/x"), response, HttpStatus.FORBIDDEN, "Forbidden", null);

        assertThat(fieldNames(body(response))).containsExactlyInAnyOrder("instance", "status", "title");
    }

    @Test
    @DisplayName("responde con el status HTTP y application/problem+json")
    void setsStatusAndContentType() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/x"), response, HttpStatus.TOO_MANY_REQUESTS, "t", "d");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    @DisplayName("escribe el detalle con tildes como bytes UTF-8 (C3 A9 y ningún E9)")
    void writesNonAsciiCharactersAsUtf8Bytes() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/x"), response, HttpStatus.FORBIDDEN, "Forbidden",
                "No tenés permiso para acceder a este recurso.");

        byte[] bytes = response.getContentAsByteArray();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\"detail\":\"No tenés permiso para acceder a este recurso.\"");
        assertThat(bytes).contains((byte) 0xC3, (byte) 0xA9).doesNotContain((byte) 0xE9);
        assertThat(body(response).get("detail").asString()).isEqualTo("No tenés permiso para acceder a este recurso.");
    }

    @Test
    @DisplayName("el Content-Type no declara charset ISO-8859-1")
    void contentTypeDoesNotDeclareLatin1() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetailResponseWriter.write(request("/api/x"), response, HttpStatus.FORBIDDEN, "Forbidden", "d");

        assertThat(response.getContentType()).doesNotContainIgnoringCase("ISO-8859-1");
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private JsonNode body(MockHttpServletResponse response) throws IOException {
        return mapper.readTree(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
