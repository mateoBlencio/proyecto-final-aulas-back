package ar.edu.utn.frc.siga.cameracount.config;

import ar.edu.utn.frc.siga.cameracount.client.HttpCameraCountClient;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import ar.edu.utn.frc.siga.cameracount.service.impl.CameraCountServiceImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("CameraCountConfiguration: cableado del cliente HTTP")
class CameraCountConfigurationTest {

    private static final String HEALTH_OK =
            "{\"estado\":\"ok\",\"modelo_cargado\":true,\"estimador\":\"yolo\",\"version\":\"0.1.0\"}";
    private static final String COUNT_OK = "{\"estado\":\"ok\",\"cantidad\":3,\"estimado\":3.4,"
            + "\"muestras_pedidas\":3,\"muestras_ok\":3,\"medido_en\":\"2026-10-24T03:43:43Z\",\"duracion_ms\":100}";
    private static final String RTSP = "rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101";
    private static final Duration SLOW_RESPONSE = Duration.ofSeconds(1);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CameraCountConfiguration.class, CameraCountServiceImpl.class);

    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();
    private final CountDownLatch releaseSlowHandlers = new CountDownLatch(1);
    private HttpServer http;

    private record ReceivedRequest(String method, String path, String apiKey, String contentType, String body) {}

    @AfterEach
    void stopServer() {
        releaseSlowHandlers.countDown();
        if (http != null) {
            http.stop(0);
        }
    }

    private String startServer(HttpHandler handler) throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            received.add(new ReceivedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-API-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            handler.handle(exchange);
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    private HttpHandler healthyService() {
        return exchange -> respond(exchange, exchange.getRequestURI().getPath().equals("/conteo") ? COUNT_OK : HEALTH_OK);
    }

    private HttpHandler slowService() {
        return exchange -> {
            try {
                releaseSlowHandlers.await(SLOW_RESPONSE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, HEALTH_OK);
        };
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException clientAlreadyGone) {
            exchange.close();
        }
    }

    private static HttpCameraCountClient clientFor(String baseUrl, String apiKey, Duration readTimeout) {
        CameraCountProperties properties = new CameraCountProperties();
        properties.setUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setReadTimeout(readTimeout);
        return new CameraCountConfiguration().httpCameraCountClient(properties);
    }

    @Test
    @DisplayName("GET /health: el bean real manda el header X-API-Key con la clave configurada")
    void healthSendsConfiguredApiKey() throws Exception {
        String baseUrl = startServer(healthyService());

        CameraCountHealthResponseDto health = clientFor(baseUrl, "clave-test", Duration.ofSeconds(5)).health();

        assertThat(health).isEqualTo(new CameraCountHealthResponseDto(true, "yolo", "0.1.0"));
        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/health");
            assertThat(request.apiKey()).isEqualTo("clave-test");
        });
    }

    @Test
    @DisplayName("POST /conteo: manda X-API-Key, Content-Type JSON y el body con rtsp_url y muestras por la red")
    void countSendsApiKeyAndJsonBodyOverTheWire() throws Exception {
        String baseUrl = startServer(healthyService());

        CameraCountResponseDto count = clientFor(baseUrl, "clave-test", Duration.ofSeconds(5)).count(RTSP, 3);

        assertThat(count.status()).isEqualTo(CameraCountStatus.OK);
        assertThat(count.people()).isEqualTo(3);
        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/conteo");
            assertThat(request.apiKey()).isEqualTo("clave-test");
            assertThat(request.contentType()).startsWith("application/json");
            assertThat(request.body()).isEqualTo("{\"rtsp_url\":\"" + RTSP + "\",\"muestras\":3}");
        });
    }

    @Test
    @DisplayName("un servidor que tarda más que el read-timeout (200 ms) termina en CameraCountUnavailableException por timeout")
    void slowServerBeyondReadTimeoutBecomesUnavailable() throws Exception {
        String baseUrl = startServer(slowService());
        HttpCameraCountClient client = clientFor(baseUrl, "clave-test", Duration.ofMillis(200));

        assertThatThrownBy(client::health)
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .hasRootCauseInstanceOf(HttpTimeoutException.class);
    }

    @Test
    @DisplayName("el mismo servidor lento con un read-timeout mayor que la demora responde bien")
    void slowServerWithinReadTimeoutResponds() throws Exception {
        String baseUrl = startServer(slowService());

        assertThat(clientFor(baseUrl, "clave-test", Duration.ofSeconds(10)).health().ready()).isTrue();
    }

    @Test
    @DisplayName("enabled=false: no hay HttpCameraCountClient, hay un CameraCountService y sus tres métodos lanzan Unavailable")
    void disabledFlagYieldsDisabledService() {
        runner.withPropertyValues("siga.camera-count.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(HttpCameraCountClient.class)
                    .hasSingleBean(CameraCountService.class);
            CameraCountService service = context.getBean(CameraCountService.class);
            assertThatThrownBy(() -> service.count(RTSP, 5)).isExactlyInstanceOf(CameraCountUnavailableException.class)
                    .hasMessageContaining("deshabilitado");
            assertThatThrownBy(() -> service.probe(RTSP)).isExactlyInstanceOf(CameraCountUnavailableException.class)
                    .hasMessageContaining("deshabilitado");
            assertThatThrownBy(service::health).isExactlyInstanceOf(CameraCountUnavailableException.class)
                    .hasMessageContaining("deshabilitado");
        });
    }

    @Test
    @DisplayName("enabled=true con url y api-key: hay un HttpCameraCountClient y el CameraCountService es CameraCountServiceImpl")
    void enabledFlagYieldsHttpClientAndService() {
        runner.withPropertyValues(
                "siga.camera-count.enabled=true",
                "siga.camera-count.url=http://conteo.test:8000",
                "siga.camera-count.api-key=clave").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(HttpCameraCountClient.class)
                    .hasSingleBean(CameraCountService.class);
            assertThat(context.getBean(CameraCountService.class)).isInstanceOf(CameraCountServiceImpl.class);
        });
    }

    @Test
    @DisplayName("enabled=true: el CameraCountService delega en el cliente real y usa url y api-key de siga.camera-count")
    void serviceDelegatesToClientBuiltFromBoundProperties() throws Exception {
        String baseUrl = startServer(healthyService());

        runner.withPropertyValues(
                "siga.camera-count.enabled=true",
                "siga.camera-count.url=" + baseUrl,
                "siga.camera-count.api-key=clave-del-contexto").run(context -> {
            assertThat(context.getBean(CameraCountService.class).health())
                    .isEqualTo(new CameraCountHealthResponseDto(true, "yolo", "0.1.0"));
            assertThat(received).singleElement().satisfies(request -> {
                assertThat(request.path()).isEqualTo("/health");
                assertThat(request.apiKey()).isEqualTo("clave-del-contexto");
            });
        });
    }

    @Test
    @DisplayName("enabled=true: count del service llega a POST /conteo del servidor con los samples pedidos")
    void serviceCountReachesTheServer() throws Exception {
        String baseUrl = startServer(healthyService());

        runner.withPropertyValues(
                "siga.camera-count.enabled=true",
                "siga.camera-count.url=" + baseUrl,
                "siga.camera-count.api-key=clave").run(context -> {
            assertThat(context.getBean(CameraCountService.class).count(RTSP, 3).people()).isEqualTo(3);
            assertThat(received).singleElement().satisfies(request -> {
                assertThat(request.path()).isEqualTo("/conteo");
                assertThat(request.body()).isEqualTo("{\"rtsp_url\":\"" + RTSP + "\",\"muestras\":3}");
            });
        });
    }

    @Test
    @DisplayName("enabled=true con read-timeout=200ms: el timeout configurado se aplica y un servidor lento da Unavailable")
    void boundReadTimeoutIsApplied() throws Exception {
        String baseUrl = startServer(slowService());

        runner.withPropertyValues(
                "siga.camera-count.enabled=true",
                "siga.camera-count.url=" + baseUrl,
                "siga.camera-count.api-key=clave",
                "siga.camera-count.read-timeout=200ms").run(context ->
                assertThatThrownBy(() -> context.getBean(CameraCountService.class).health())
                        .isExactlyInstanceOf(CameraCountUnavailableException.class));
    }

    static Stream<Arguments> invalidEnabledConfigurations() {
        return Stream.of(
                arguments(named("sin api-key", new String[] {
                        "siga.camera-count.url=http://conteo.test"}), "apiKey"),
                arguments(named("api-key vacía (default del yaml con CONTEO_API_KEY sin definir)", new String[] {
                        "siga.camera-count.url=http://conteo.test", "siga.camera-count.api-key="}), "apiKey"),
                arguments(named("api-key en blanco", new String[] {
                        "siga.camera-count.url=http://conteo.test", "siga.camera-count.api-key=   "}), "apiKey"),
                arguments(named("sin url", new String[] {
                        "siga.camera-count.api-key=clave"}), "url"),
                arguments(named("url vacía (default del yaml con CONTEO_URL sin definir)", new String[] {
                        "siga.camera-count.url=", "siga.camera-count.api-key=clave"}), "url"),
                arguments(named("url ftp://x", new String[] {
                        "siga.camera-count.url=ftp://x", "siga.camera-count.api-key=clave"}), "debe ser una URL http o https")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEnabledConfigurations")
    @DisplayName("enabled=true con configuración inválida: el contexto falla al arrancar y nombra la propiedad")
    void enabledWithInvalidConfigurationFailsStartup(String[] properties, String expectedInMessage) {
        runner.withPropertyValues("siga.camera-count.enabled=true")
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedInMessage);
                });
    }

    @Test
    @DisplayName("enabled=false con url inválida: no valida nada y el contexto arranca con el service")
    void disabledFlagIgnoresInvalidConnectionProperties() {
        runner.withPropertyValues("siga.camera-count.enabled=false", "siga.camera-count.url=ftp://x")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CameraCountService.class));
    }
}
