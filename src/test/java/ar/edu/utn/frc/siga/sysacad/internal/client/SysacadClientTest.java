package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadResponseTooLargeException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("SysacadClient: lectura HTTP de vistas")
class SysacadClientTest {

    private static final String URL = "http://sysacad.test/api/data/views/Edificios";
    private static final ParameterizedTypeReference<ViewResponse<RawBuilding>> TYPE =
            new ParameterizedTypeReference<>() {};
    private static final String TWO_ROWS = "{\"rows\":[{\"Edificio\":1,\"EdificioNombre\":\"A\"},"
            + "{\"Edificio\":2,\"EdificioNombre\":\"B\"}]}";

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private SysacadClient client;

    @BeforeEach
    void setUp() {
        client = clientWithLimit(1_000_000);
    }

    private SysacadClient clientWithLimit(long maxBytes) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://sysacad.test")
                .requestInterceptor(new ResponseSizeLimitInterceptor(maxBytes));
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps.clear();
        return new SysacadClient(builder.build(), 3, sleeps::add);
    }

    @Test
    @DisplayName("503, 503, 200: reintenta con backoff exponencial y devuelve las filas")
    void retriesTransientErrorsThenReturnsRows() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess(TWO_ROWS, MediaType.APPLICATION_JSON));

        List<RawBuilding> rows = client.fetchRows("Edificios", ViewQuery.none(), TYPE);

        assertThat(rows).hasSize(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    @DisplayName("503 en todos los intentos: tras maxRetries propaga el error HTTP (4 pedidos)")
    void givesUpAfterMaxRetries() {
        for (int i = 0; i < 4; i++) {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        assertThatThrownBy(() -> client.fetchRows("Edificios", ViewQuery.none(), TYPE))
                .isInstanceOf(HttpServerErrorException.class);

        assertThat(sleeps).hasSize(3);
        server.verify();
    }

    @Test
    @DisplayName("404: no reintenta")
    void doesNotRetryClientErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchRows("Edificios", ViewQuery.none(), TYPE))
                .isInstanceOf(HttpClientErrorException.class);

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("429 con Retry-After espera lo que indica el servidor")
    void honorsRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        server.expect(requestTo(URL)).andRespond(withSuccess(TWO_ROWS, MediaType.APPLICATION_JSON));

        client.fetchRows("Edificios", ViewQuery.none(), TYPE);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("error de E/S (timeout o conexión caída): SysacadUnavailableException sin reintentar")
    void ioErrorBecomesUnavailable() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.fetchRows("Edificios", ViewQuery.none(), TYPE))
                .isInstanceOf(SysacadUnavailableException.class)
                .hasMessageContaining("Edificios");

        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("{\"rows\": null} y cuerpo sin rows devuelven lista vacía")
    void nullRowsBecomeEmptyList() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"rows\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchRows("Edificios", ViewQuery.none(), TYPE)).isEmpty();
        assertThat(client.fetchRows("Edificios", ViewQuery.none(), TYPE)).isEmpty();
    }

    @Test
    @DisplayName("una respuesta que supera el máximo de bytes se corta con SysacadResponseTooLargeException")
    void rejectsOversizedBody() {
        SysacadClient small = clientWithLimit(40);
        server.expect(requestTo(URL)).andRespond(withSuccess(TWO_ROWS, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> small.fetchRows("Edificios", ViewQuery.none(), TYPE))
                .isInstanceOf(SysacadResponseTooLargeException.class);
    }

    @Test
    @DisplayName("una respuesta justo dentro del máximo se lee completa")
    void acceptsBodyWithinLimit() {
        SysacadClient exact = clientWithLimit(TWO_ROWS.getBytes().length);
        server.expect(requestTo(URL)).andRespond(withSuccess(TWO_ROWS, MediaType.APPLICATION_JSON));

        assertThat(exact.fetchRows("Edificios", ViewQuery.none(), TYPE)).hasSize(2);
    }

    @Test
    @DisplayName("cuerpo chunked (sin Content-Length) mayor al tope contra un servidor HTTP real: se corta con SysacadResponseTooLargeException")
    void rejectsOversizedChunkedBodyWithoutContentLength() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked transfer encoding
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("{\"rows\":[".getBytes());
                byte[] row = "{\"Edificio\":1,\"EdificioNombre\":\"A\"},".getBytes();
                for (int i = 0; i < 50; i++) {
                    out.write(row);
                }
                out.write("{\"Edificio\":2}]}".getBytes());
            }
        });
        http.start();
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + http.getAddress().getPort())
                    .requestInterceptor(new ResponseSizeLimitInterceptor(500))
                    .build();
            SysacadClient real = new SysacadClient(restClient, 0, sleeps::add);

            assertThatThrownBy(() -> real.fetchRows("Edificios", ViewQuery.none(), TYPE))
                    .isInstanceOf(SysacadResponseTooLargeException.class);
        } finally {
            http.stop(0);
        }
    }
}
