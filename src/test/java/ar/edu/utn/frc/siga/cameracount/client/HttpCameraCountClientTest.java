package ar.edu.utn.frc.siga.cameracount.client;

import ar.edu.utn.frc.siga.cameracount.client.dto.CameraRequest;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraProbeResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountFailedException;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.exception.InvalidCameraUrlException;
import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.model.CameraProbeFailure;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Named.named;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("HttpCameraCountClient: traducción de /conteo, /probar y /health")
class HttpCameraCountClientTest {

    private static final String BASE = "http://conteo.test";
    private static final String COUNT_URL = BASE + "/conteo";
    private static final String PROBE_URL = BASE + "/probar";
    private static final String HEALTH_URL = BASE + "/health";
    private static final String RTSP = "rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101";
    private static final String MASKED_TARGET = "rtsp://***@172.16.1.10:554/...";

    private static final String COUNT_OK = "{\"estado\":\"ok\",\"cantidad\":3,\"estimado\":3.4086,"
            + "\"muestras_pedidas\":5,\"muestras_ok\":5,\"medido_en\":\"2026-10-24T03:43:43Z\",\"duracion_ms\":24117}";
    private static final String COUNT_NO_SIGNAL = "{\"estado\":\"sin_senal\",\"cantidad\":null,\"estimado\":null,"
            + "\"muestras_pedidas\":5,\"muestras_ok\":0,\"medido_en\":\"2026-10-24T03:43:43Z\",\"duracion_ms\":1200}";
    private static final String SATURATED = "{\"detalle\":\"servicio saturado\"}";

    private MockRestServiceServer server;
    private HttpCameraCountClient client;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger clientLogger;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCameraCountClient(builder.build());

        clientLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpCameraCountClient.class);
        previousLevel = clientLogger.getLevel();
        clientLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(logs);
        clientLogger.setLevel(previousLevel);
    }

    private static HttpHeaders retryAfter(String seconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, seconds);
        return headers;
    }

    private static ResponseCreator json(int status, String body) {
        return withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String requestBody(String rtspUrl, Integer samples) {
        return "{\"rtsp_url\":\"" + rtspUrl + "\"" + (samples == null ? "" : ",\"muestras\":" + samples) + "}";
    }

    private void expectCount(ResponseCreator response) {
        server.expect(requestTo(COUNT_URL)).andExpect(method(HttpMethod.POST)).andRespond(response);
    }

    private void expectProbe(ResponseCreator response) {
        server.expect(requestTo(PROBE_URL)).andExpect(method(HttpMethod.POST)).andRespond(response);
    }

    private void expectHealth(ResponseCreator response) {
        server.expect(requestTo(HEALTH_URL)).andExpect(method(HttpMethod.GET)).andRespond(response);
    }

    private static void assertNoCameraUrlIn(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(String.valueOf(t.getMessage())).as("mensaje de %s", t.getClass().getSimpleName())
                    .doesNotContain("s3cr3t", "admin", "172.16.1.10");
        }
    }

    @Test
    @DisplayName("conteo ok: mapea todos los campos y manda POST /conteo con JSON {rtsp_url, muestras}")
    void countOkMapsAllFieldsAndSendsExactBody() {
        server.expect(requestTo(COUNT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(requestBody(RTSP, 5)))
                .andRespond(withSuccess(COUNT_OK, MediaType.APPLICATION_JSON));

        CameraCountResponseDto count = client.count(RTSP, 5);

        assertThat(count).isEqualTo(new CameraCountResponseDto(CameraCountStatus.OK, 3, 3.4086, 5, 5,
                Instant.parse("2026-10-24T03:43:43Z"), 24117L));
        server.verify();
    }

    @Test
    @DisplayName("conteo sin_senal con cantidad y estimado null: NO_SIGNAL con people y estimate null")
    void countNoSignalHasNullPeopleAndEstimate() {
        expectCount(withSuccess(COUNT_NO_SIGNAL, MediaType.APPLICATION_JSON));

        CameraCountResponseDto count = client.count(RTSP, 5);

        assertThat(count.status()).isEqualTo(CameraCountStatus.NO_SIGNAL);
        assertThat(count.people()).isNull();
        assertThat(count.estimate()).isNull();
        assertThat(count.samplesOk()).isZero();
        assertThat(count.durationMs()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("campos desconocidos en la respuesta se ignoran")
    void countIgnoresUnknownResponseFields() {
        expectCount(withSuccess(COUNT_OK.replace("{", "{\"campo_nuevo\":\"x\","), MediaType.APPLICATION_JSON));

        assertThat(client.count(RTSP, 5).people()).isEqualTo(3);
    }

    @Test
    @DisplayName("samples null: el body no incluye la clave muestras")
    void nullSamplesOmitsMuestrasKey() {
        server.expect(requestTo(COUNT_URL))
                .andExpect(content().string(requestBody(RTSP, null)))
                .andRespond(withSuccess(COUNT_OK, MediaType.APPLICATION_JSON));

        client.count(RTSP, null);

        server.verify();
    }

    @ParameterizedTest(name = "samples={0} es válido y se envía tal cual")
    @ValueSource(ints = {1, 10})
    @DisplayName("samples 1 y 10: el cliente los manda tal cual")
    void samplesAtRangeBoundariesAreSent(int samples) {
        server.expect(requestTo(COUNT_URL))
                .andExpect(content().string(requestBody(RTSP, samples)))
                .andRespond(withSuccess(COUNT_OK, MediaType.APPLICATION_JSON));

        client.count(RTSP, samples);

        server.verify();
    }

    @Test
    @DisplayName("503 con Retry-After: Unavailable (503) que conserva el header Retry-After=10")
    void saturatedServiceKeepsRetryAfter() {
        expectCount(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(retryAfter("10"))
                .contentType(MediaType.APPLICATION_JSON).body(SATURATED));

        assertThatThrownBy(() -> client.count(RTSP, 5))
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .isInstanceOfSatisfying(CameraCountUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getHeaders()).containsExactly(entry("Retry-After", "10"));
                });
    }

    @Test
    @DisplayName("503 sin Retry-After: Unavailable sin header")
    void unavailableWithoutRetryAfterHasNoHeader() {
        expectCount(json(503, SATURATED));

        assertThatThrownBy(() -> client.count(RTSP, 5))
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .isInstanceOfSatisfying(CameraCountUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getHeaders()).isEmpty();
                });
    }

    @Test
    @DisplayName("error de E/S (conexión caída o timeout): Unavailable con la causa original")
    void ioErrorBecomesUnavailableWithCause() {
        expectCount(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.count(RTSP, 5))
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class)
                .hasRootCauseMessage("Read timed out")
                .isInstanceOfSatisfying(CameraCountUnavailableException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {401, 404, 500, 502, 504})
    @DisplayName("401 y otros 4xx/5xx: CameraCountFailedException (502) sin causa")
    void otherHttpErrorsBecomeFailed(int status) {
        expectCount(json(status, "{\"detalle\":\"error\"}"));

        assertThatThrownBy(() -> client.count(RTSP, 5))
                .isExactlyInstanceOf(CameraCountFailedException.class)
                .hasNoCause()
                .isInstanceOfSatisfying(CameraCountFailedException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    @DisplayName("422: InvalidCameraUrlException (422)")
    void unprocessableBecomesInvalidCameraUrl() {
        expectCount(json(422, "{\"detail\":[{\"loc\":[\"body\",\"rtsp_url\"],\"msg\":\"Value error, host no permitido\"}]}"));

        assertThatThrownBy(() -> client.count(RTSP, 5))
                .isExactlyInstanceOf(InvalidCameraUrlException.class)
                .isInstanceOfSatisfying(InvalidCameraUrlException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(422));
    }

    @Test
    @DisplayName("estado desconocido en /conteo: CameraCountFailedException")
    void unknownCountStatusBecomesFailed() {
        expectCount(withSuccess(COUNT_OK.replace("\"ok\"", "\"raro\""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.count(RTSP, 5)).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    @Test
    @DisplayName("200 sin body: CameraCountFailedException")
    void emptyBodyBecomesFailed() {
        expectCount(withSuccess());

        assertThatThrownBy(() -> client.count(RTSP, 5)).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    @Test
    @DisplayName("200 con un body que no es JSON (por ejemplo, una página de un proxy): CameraCountFailedException")
    void nonJsonBodyBecomesFailed() {
        expectCount(withSuccess("<html>bad gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.count(RTSP, 5)).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    @Test
    @DisplayName("probar: manda POST /probar con {rtsp_url} sin muestras y mapea una cámara que conecta")
    void probeConnectingCameraMapsDimensions() {
        server.expect(requestTo(PROBE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(requestBody(RTSP, null)))
                .andRespond(withSuccess(
                        "{\"conecta\":true,\"ancho\":1920,\"alto\":1080,\"motivo\":null,\"duracion_ms\":850}",
                        MediaType.APPLICATION_JSON));

        CameraProbeResponseDto probe = client.probe(RTSP);

        assertThat(probe).isEqualTo(new CameraProbeResponseDto(true, 1920, 1080, null, 850L));
        server.verify();
    }

    @ParameterizedTest(name = "motivo \"{0}\" -> {1}")
    @CsvSource({"no_conecta,NOT_CONNECTING", "sin_fotograma,NO_FRAME", "timeout,TIMEOUT"})
    @DisplayName("probar con conecta=false: traduce cada motivo del servicio")
    void probeFailureReasonIsTranslated(String reason, CameraProbeFailure expected) {
        expectProbe(withSuccess(
                "{\"conecta\":false,\"ancho\":null,\"alto\":null,\"motivo\":\"" + reason + "\",\"duracion_ms\":10000}",
                MediaType.APPLICATION_JSON));

        CameraProbeResponseDto probe = client.probe(RTSP);

        assertThat(probe.connects()).isFalse();
        assertThat(probe.failure()).isEqualTo(expected);
        assertThat(probe.width()).isNull();
        assertThat(probe.height()).isNull();
    }

    @Test
    @DisplayName("probar con un motivo desconocido: CameraCountFailedException")
    void unknownProbeReasonBecomesFailed() {
        expectProbe(withSuccess(
                "{\"conecta\":false,\"ancho\":null,\"alto\":null,\"motivo\":\"raro\",\"duracion_ms\":1}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.probe(RTSP)).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    @Test
    @DisplayName("probar con 422: InvalidCameraUrlException")
    void probeUnprocessableBecomesInvalidCameraUrl() {
        expectProbe(json(422, "{\"detail\":[{\"msg\":\"Value error, host no permitido\"}]}"));

        assertThatThrownBy(() -> client.probe(RTSP)).isExactlyInstanceOf(InvalidCameraUrlException.class);
    }

    @Test
    @DisplayName("probar con error de E/S: Unavailable")
    void probeIoErrorBecomesUnavailable() {
        expectProbe(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.probe(RTSP)).isExactlyInstanceOf(CameraCountUnavailableException.class);
    }

    @Test
    @DisplayName("health 200 con estado ok: ready=true con estimador y versión")
    void healthOkIsReady() {
        expectHealth(withSuccess(
                "{\"estado\":\"ok\",\"modelo_cargado\":true,\"estimador\":\"yolo\",\"version\":\"0.1.0\"}",
                MediaType.APPLICATION_JSON));

        assertThat(client.health()).isEqualTo(new CameraCountHealthResponseDto(true, "yolo", "0.1.0"));
        server.verify();
    }

    @Test
    @DisplayName("health 503 con estado cargando: ready=false y lee el body en vez de lanzar")
    void healthLoadingModelIsNotReady() {
        expectHealth(json(503,
                "{\"estado\":\"cargando\",\"modelo_cargado\":false,\"estimador\":null,\"version\":\"0.1.0\"}"));

        assertThat(client.health()).isEqualTo(new CameraCountHealthResponseDto(false, null, "0.1.0"));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {401, 404, 500})
    @DisplayName("health con otro status (401, 404, 500): CameraCountFailedException")
    void healthOtherStatusBecomesFailed(int status) {
        expectHealth(json(status, "{\"detalle\":\"error\"}"));

        assertThatThrownBy(() -> client.health()).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    @Test
    @DisplayName("health con error de E/S: Unavailable")
    void healthIoErrorBecomesUnavailable() {
        expectHealth(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.health()).isExactlyInstanceOf(CameraCountUnavailableException.class);
    }

    @Test
    @DisplayName("health con estado desconocido: CameraCountFailedException")
    void healthUnknownStateBecomesFailed() {
        expectHealth(withSuccess(
                "{\"estado\":\"raro\",\"modelo_cargado\":true,\"estimador\":null,\"version\":\"0.1.0\"}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.health()).isExactlyInstanceOf(CameraCountFailedException.class);
    }

    static Stream<Named<ResponseCreator>> failingResponses() {
        String echoedUrl = "{\"detail\":\"url inválida: " + RTSP + "\"}";
        return Stream.of(
                named("503 con Retry-After", withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(retryAfter("10"))
                        .contentType(MediaType.APPLICATION_JSON).body(echoedUrl)),
                named("503 sin Retry-After", json(503, echoedUrl)),
                named("422", json(422, echoedUrl)),
                named("401", json(401, echoedUrl)),
                named("500", json(500, echoedUrl)),
                named("IOException", withException(new IOException("Connection reset")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingResponses")
    @DisplayName("count: ninguna excepción ni su cadena de causas contiene usuario, clave ni host de la URL RTSP, ni siquiera si el servicio la repite en su respuesta")
    void countExceptionsNeverContainCredentials(ResponseCreator response) {
        expectCount(response);

        Throwable thrown = catchThrowable(() -> client.count(RTSP, 5));

        assertThat(thrown).isNotNull();
        assertNoCameraUrlIn(thrown);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingResponses")
    @DisplayName("probe: ninguna excepción ni su cadena de causas contiene usuario, clave ni host de la URL RTSP")
    void probeExceptionsNeverContainCredentials(ResponseCreator response) {
        expectProbe(response);

        Throwable thrown = catchThrowable(() -> client.probe(RTSP));

        assertThat(thrown).isNotNull();
        assertNoCameraUrlIn(thrown);
    }

    @Test
    @DisplayName("los logs del cliente, de éxitos y de errores, no llevan usuario ni clave y los errores traen la URL enmascarada")
    void logsNeverContainCredentials() {
        expectCount(withSuccess(COUNT_OK, MediaType.APPLICATION_JSON));
        expectProbe(withSuccess("{\"conecta\":true,\"ancho\":1,\"alto\":1,\"motivo\":null,\"duracion_ms\":1}",
                MediaType.APPLICATION_JSON));
        expectCount(json(503, SATURATED));
        expectCount(json(422, SATURATED));
        expectCount(json(401, SATURATED));
        expectCount(json(500, SATURATED));
        expectCount(withException(new IOException("Connection reset")));

        client.count(RTSP, 5);
        client.probe(RTSP);
        for (int i = 0; i < 5; i++) {
            catchThrowable(() -> client.count(RTSP, 5));
        }

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(MASKED_TARGET))
                .allSatisfy(message -> assertThat(message).doesNotContain("s3cr3t", "admin"));
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).contains(Level.WARN, Level.ERROR);
    }

    @Test
    @DisplayName("CameraRequest.toString oculta la clave y deja el host enmascarado")
    void cameraRequestToStringMasksUrl() {
        String text = new CameraRequest(RTSP, 5).toString();

        assertThat(text).doesNotContain("s3cr3t", "admin").contains("***@172.16.1.10:554");
    }
}
