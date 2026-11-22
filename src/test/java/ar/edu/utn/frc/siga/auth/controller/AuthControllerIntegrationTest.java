package ar.edu.utn.frc.siga.auth.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
@Import(AuthControllerIntegrationTest.FakeChannelSenderConfig.class)
@Sql(scripts = "/auth/integration/seed-admin.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/auth/integration/cleanup-auth.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthControllerIntegrationTest {

    private static final String ADMIN_EMAIL = "admin.test@frc.utn.edu.ar";
    private static final String AUXILIAR_EMAIL = "auxiliar.test@frc.utn.edu.ar";
    private static final String PASSWORD = "TestPassword123!";
    private static final String PASSWORD_HASH = "$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi";
    private static final String NEW_PASSWORD = "Cuaderno-Violeta-7391";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeChannelSender fakeChannelSender;

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
    void login_shouldReturn200WithTokens_whenEmailUsesInstitutionalSubdomain() throws Exception {
        String email = "subdominio.test@sistemas.frc.utn.edu.ar";
        ResponseEntity<String> response = login(email, PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = parseMap(response);
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("email")).isEqualTo(email);
    }

    @Test
    void login_shouldReturn200ForLowercaseUser_whenEmailHasUppercase() throws Exception {
        ResponseEntity<String> response = login("Subdominio.Test@SISTEMAS.frc.utn.edu.ar", PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseMap(response).get("email")).isEqualTo("subdominio.test@sistemas.frc.utn.edu.ar");
    }

    @Test
    void login_shouldReturn400_whenEmailHasSurroundingSpaces() throws Exception {
        ResponseEntity<String> response = login("  subdominio.test@sistemas.frc.utn.edu.ar  ", PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseMap(response).get("errors")).asString().contains("email");
    }

    @Test
    void login_shouldReturn401_whenPasswordIsWrong() {
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
        String email = "ratelimited.test@frc.utn.edu.ar";
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = login(email, "wrong-password");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> sixth = login(email, "wrong-password");
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

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

    @Test
    void import_shouldReturn403_forAuxiliarAulico() throws Exception {
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
                baseUrl + "/v1/imports", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

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

        postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

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

    @Test
    void forgotPassword_shouldReturn202WithEmptyBody_whenEmailExistsAndWhenItDoesNot() {
        String existing = createUser("recupero.existe");
        String unknown = "recupero.noexiste." + System.nanoTime() + "@frc.utn.edu.ar";

        ResponseEntity<String> forExisting = forgotPassword(existing);
        ResponseEntity<String> forUnknown = forgotPassword(unknown);

        assertThat(forExisting.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(forExisting.getBody()).isNullOrEmpty();
        assertThat(forUnknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(forUnknown.getBody()).isNullOrEmpty();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(fakeChannelSender.sentTo(existing)).hasSize(1));
        assertThat(fakeChannelSender.sentTo(unknown)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notificacion WHERE destinatario = ?", Integer.class, unknown)).isZero();
    }

    @Test
    void forgotPassword_shouldReturn429WithProblemShapeAndRetryAfter_onTheFourthRequestForTheSameEmailAndIp()
            throws Exception {
        String email = createUser("recupero.limite");

        for (int i = 0; i < 3; i++) {
            assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        ResponseEntity<String> fourth = forgotPassword(email);

        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fourth.getHeaders().getFirst("Retry-After")).matches("\\d+");
        assertThat(Long.parseLong(fourth.getHeaders().getFirst("Retry-After"))).isPositive();
        assertThat(parseMap(fourth).keySet()).containsExactlyInAnyOrder("detail", "instance", "status", "title");
    }

    @Test
    void resetPassword_shouldChangePasswordRevokeSessionsAndConsumeTheToken_inTheFullFlow() throws Exception {
        String email = createUser("recupero.flujo");
        String oldRefreshToken = (String) parseMap(login(email, PASSWORD)).get("refreshToken");

        assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String token = awaitResetToken(email);
        ResponseEntity<String> reset = resetPassword(token, NEW_PASSWORD);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> refresh = postJson("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(oldRefreshToken));
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> reuse = resetPassword(token, "Otra-Clave-Distinta-5528");
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseMap(reuse)).doesNotContainKey("errors");
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM token_recuperacion_clave t JOIN usuario u ON u.id_usuario = t.id_usuario "
                        + "WHERE u.correo = ? AND t.fecha_uso IS NOT NULL", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void resetPassword_shouldReturn400WithNewPasswordError_andKeepTheTokenUsable_whenPasswordIsWeak()
            throws Exception {
        String email = createUser("recupero.debil");
        assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String token = awaitResetToken(email);

        ResponseEntity<String> weak = resetPassword(token, "corta");

        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseMap(weak).get("errors")).asString().contains("newPassword");
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resetPassword(token, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void resetPassword_shouldReturn400WithoutErrors_whenTokenDoesNotExist() throws Exception {
        ResponseEntity<String> response = resetPassword("token-que-nunca-se-emitio", NEW_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseMap(response)).doesNotContainKey("errors");
    }

    @Test
    void passwordResetNotification_shouldEndSentWithRedactedBodyAndNoCopies() {
        String email = createUser("recupero.redactada");

        assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitResetToken(email);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT estado, cuerpo, cc, cco FROM notificacion WHERE plantilla = 'PASSWORD_RESET' "
                            + "AND destinatario = ?", email);
            assertThat(row).containsEntry("estado", "SENT").containsEntry("cuerpo", "[contenido omitido]")
                    .containsEntry("cc", null).containsEntry("cco", null);
        });
    }

    private ResponseEntity<String> forgotPassword(String email) {
        return postJson("/auth/forgot-password", """
                {"email":"%s"}
                """.formatted(email));
    }

    private ResponseEntity<String> resetPassword(String token, String newPassword) {
        return postJson("/auth/reset-password", """
                {"token":"%s","newPassword":"%s"}
                """.formatted(token, newPassword));
    }

    private String createUser(String localPart) {
        String email = localPart + "." + System.nanoTime() + "@frc.utn.edu.ar";
        jdbcTemplate.update("INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido) "
                + "VALUES (?, ?, true, 'Recupero', 'Prueba')", email, PASSWORD_HASH);
        return email;
    }

    private String awaitResetToken(String email) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(fakeChannelSender.sentTo(email)).isNotEmpty());
        Matcher matcher = TOKEN_IN_LINK.matcher(fakeChannelSender.sentTo(email).getFirst().body());
        assertThat(matcher.find()).as("enlace con #token= en el cuerpo").isTrue();
        return matcher.group(1);
    }

    static class FakeChannelSender implements ChannelSender {

        private final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void send(RenderedNotification message, NotificationRecipient recipient) {
            sent.add(new Sent(recipient.email(), message.subject(), message.body()));
        }

        List<Sent> sentTo(String email) {
            return sent.stream()
                    .filter(message -> message.email().equals(email)
                            && message.subject().startsWith("Restablecimiento"))
                    .toList();
        }

        record Sent(String email, String subject, String body) {
        }
    }

    @TestConfiguration
    static class FakeChannelSenderConfig {

        @Bean
        FakeChannelSender fakeChannelSender() {
            return new FakeChannelSender();
        }
    }
}
