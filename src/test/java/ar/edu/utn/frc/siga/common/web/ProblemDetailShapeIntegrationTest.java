package ar.edu.utn.frc.siga.common.web;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.common.config.CorsProperties;
import ar.edu.utn.frc.siga.common.config.GeneralRateLimitProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Los 401, 403 y 429 (escritos a mano por filtros y handlers de seguridad) tienen que salir con la misma
 * forma que los errores del GlobalExceptionHandler. Cadena de seguridad real; el RateLimitFilter se agrega al
 * MockMvc porque MockMvc no toma los filtros registrados con FilterRegistrationBean.
 */
@DisplayName("Forma de los ProblemDetail de 401, 403, 404 y 429 (integracion)")
class ProblemDetailShapeIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> EXPECTED_KEYS = List.of("detail", "instance", "status", "title");
    private static final String MISSING_BUILDING = "/v1/buildings/999999999/activation";
    private static final String CONSULTA_EMAIL = "consulta-shape@frc.utn.edu.ar";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("un 401 sin token y un 404 del handler tienen el mismo conjunto de claves, sin nulls ni properties")
    void unauthenticatedAndNotFoundShareTheSameShape() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();

        MvcResult unauthorized = anonymous.perform(get("/v1/buildings")).andReturn();
        MvcResult notFound = mockMvc.perform(put(MISSING_BUILDING)).andReturn();

        JsonNode unauthorizedBody = assertProblem(unauthorized, 401, "/v1/buildings");
        JsonNode notFoundBody = assertProblem(notFound, 404, MISSING_BUILDING);
        assertThat(keys(unauthorizedBody)).isEqualTo(keys(notFoundBody));
        assertThat(keys(notFoundBody)).containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("un 403 de un usuario sin permiso tiene la misma forma, con instance igual a la ruta pedida")
    void forbiddenUserGetsTheSameShape() throws Exception {
        MockMvc consulta = mockMvcAs(CONSULTA_EMAIL, SystemRole.CONSULTA);

        MvcResult forbidden = consulta.perform(put(MISSING_BUILDING)).andReturn();

        JsonNode body = assertProblem(forbidden, 403, MISSING_BUILDING);
        assertThat(keys(body)).containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("un 429 del RateLimitFilter tiene la misma forma y el header Retry-After")
    void rateLimitedRequestGetsTheSameShape() throws Exception {
        mockMvcAs(CONSULTA_EMAIL, SystemRole.CONSULTA);
        GeneralRateLimitProperties properties = new GeneralRateLimitProperties();
        properties.setCapacity(1);
        properties.setRefillPeriodSeconds(60);
        CorsProperties cors = new CorsProperties();
        cors.setAllowedOrigins(List.of());
        MockMvc limited = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .addFilters(new RateLimitFilter(properties, cors))
                .build();
        String token = jwtService.generateAccessToken(CONSULTA_EMAIL);

        limited.perform(authorized(get("/v1/buildings"), token)).andReturn();
        MvcResult tooMany = limited.perform(authorized(get("/v1/buildings"), token)).andReturn();

        JsonNode body = assertProblem(tooMany, 429, "/v1/buildings");
        assertThat(keys(body)).containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
        assertThat(tooMany.getResponse().getHeader("Retry-After")).isNotNull();
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode assertProblem(MvcResult result, int status, String path) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("instance").asString()).isEqualTo(path);
        assertThat(body.has("type")).isFalse();
        assertThat(body.has("properties")).isFalse();
        body.forEach(value -> assertThat(value.isNull()).as("no hay claves con null").isFalse());
        return body;
    }

    private static List<String> keys(JsonNode body) {
        List<String> keys = new ArrayList<>();
        body.propertyNames().forEach(keys::add);
        keys.sort(String::compareTo);
        return keys;
    }
}
