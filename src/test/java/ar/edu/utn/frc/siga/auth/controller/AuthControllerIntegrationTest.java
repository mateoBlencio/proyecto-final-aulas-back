package ar.edu.utn.frc.siga.auth.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Sql(scripts = "/auth/integration/seed-admin.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/auth/integration/cleanup-auth.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthControllerIntegrationTest {

    private static final String ADMIN_EMAIL = "admin.test@frc.utn.edu.ar";
    private static final String AUXILIAR_EMAIL = "auxiliar.test@frc.utn.edu.ar";
    private static final String PASSWORD = "TestPassword123!";

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        baseUrl = "http://localhost:" + port + "/api";
    }

    private Map<String, Object> parseMap(ResponseEntity<String> response) throws Exception {
        return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> login(String email, String password) {
        return postJson("/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────────

    @Test
    void login_shouldReturn200WithTokens_whenCredentialsAreValid() throws Exception {
        ResponseEntity<String> response = login(ADMIN_EMAIL, PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = parseMap(response);
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("refreshToken")).isNotNull();
        assertThat(body.get("email")).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void login_shouldReturn401_whenPasswordIsWrong() {
        // Email dedicado (no ADMIN_EMAIL) para no gastar su contador de LoginRateLimiter,
        // que es un bean singleton compartido entre todos los tests de este contexto.
        ResponseEntity<String> response = login("wrongpass.test@frc.utn.edu.ar", "wrong-password");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_shouldReturn401_whenUserDoesNotExist() {
        ResponseEntity<String> response = login("nadie@frc.utn.edu.ar", PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_shouldReturn401AndNeverRateLimit_whenEmailOutsideInstitutionalDomain() {
        for (int i = 0; i < 7; i++) {
            ResponseEntity<String> response = login("admin@gmail.com", "cualquiera");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void login_sixthFailedAttempt_shouldReturn429() {
        // Email dedicado, aislado del resto de los tests (ver nota más arriba).
        String email = "ratelimited.test@frc.utn.edu.ar";
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = login(email, "wrong-password");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> sixth = login(email, "wrong-password");
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── ACCESO A /v1/** ────────────────────────────────────────────────────

    @Test
    void buildings_shouldReturn401_withoutToken() {
        ResponseEntity<String> response = getWithToken("/v1/buildings", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void buildings_shouldReturn200_withValidToken() throws Exception {
        String token = (String) parseMap(login(ADMIN_EMAIL, PASSWORD)).get("accessToken");
        ResponseEntity<String> response = getWithToken("/v1/buildings", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void actuatorHealth_shouldBeAccessible_withoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── CA6: control de acceso por rol ────────────────────────────────────

    @Test
    void excelImport_shouldReturn403_forAuxiliarAulico() throws Exception {
        String token = (String) parseMap(login(AUXILIAR_EMAIL, PASSWORD)).get("accessToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("irrelevante".getBytes()) {
            @Override
            public String getFilename() {
                return "test.xlsx";
            }
        });

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/v1/excelimports", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── REFRESH / LOGOUT ───────────────────────────────────────────────────

    @Test
    void refresh_shouldReturnFreshPair_whenTokenIsValid() throws Exception {
        Map<String, Object> loginBody = parseMap(login(ADMIN_EMAIL, PASSWORD));
        String refreshToken = (String) loginBody.get("refreshToken");

        ResponseEntity<String> refreshResponse = postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> refreshed = parseMap(refreshResponse);
        assertThat(refreshed.get("accessToken")).isNotNull();
        assertThat(refreshed.get("refreshToken")).isNotEqualTo(refreshToken);
    }

    @Test
    void refresh_shouldReturnFreshPair_whenRetriedImmediatelyWithOldToken_withinGraceWindow() throws Exception {
        Map<String, Object> loginBody = parseMap(login(ADMIN_EMAIL, PASSWORD));
        String refreshToken = (String) loginBody.get("refreshToken");

        // Primera rotación.
        postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

        // Reintento inmediato con el token viejo (ya rotado) dentro de la ventana de gracia.
        ResponseEntity<String> retry = postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseMap(retry).get("accessToken")).isNotNull();
    }

    @Test
    void refresh_shouldReturn401_afterLogout_evenWithinGraceWindow() throws Exception {
        Map<String, Object> loginBody = parseMap(login(ADMIN_EMAIL, PASSWORD));
        String refreshToken = (String) loginBody.get("refreshToken");

        ResponseEntity<String> logoutResponse = postJson("/auth/logout", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> refreshAfterLogout = postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_shouldBeIdempotent() throws Exception {
        Map<String, Object> loginBody = parseMap(login(ADMIN_EMAIL, PASSWORD));
        String refreshToken = (String) loginBody.get("refreshToken");

        String logoutBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

        assertThat(postJson("/auth/logout", logoutBody).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(postJson("/auth/logout", logoutBody).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void refresh_shouldReturn401_whenTokenDoesNotExist() {
        ResponseEntity<String> response = postJson("/auth/refresh", """
                {"refreshToken":"token-inexistente"}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
