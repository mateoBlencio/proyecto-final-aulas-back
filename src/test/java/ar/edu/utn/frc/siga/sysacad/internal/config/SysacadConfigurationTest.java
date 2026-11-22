package ar.edu.utn.frc.siga.sysacad.internal.config;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadConfiguration: un RestClient por cliente")
class SysacadConfigurationTest {

    private static final long SERVER_DELAY_MS = 1500;
    private static final ParameterizedTypeReference<ViewResponse<RawSchedule>> SCHEDULE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SysacadConfiguration.class, SysacadClient.class, SysacadWriteClient.class);

    private HttpServer http;

    @AfterEach
    void stopServer() {
        if (http != null) {
            http.stop(0);
        }
    }

    private String startSlowServer() throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            try {
                Thread.sleep(SERVER_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"rows\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) {
            } finally {
                exchange.close();
            }
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    @Test
    @DisplayName("hay dos RestClient y cada cliente usa el suyo")
    void eachClientGetsItsOwnRestClient() {
        runner.withPropertyValues(
                "siga.sysacad.enabled=true",
                "siga.sysacad.base-url=https://sysacad.example.test",
                "siga.sysacad.api-key=clave").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(RestClient.class))
                    .containsOnlyKeys("sysacadRestClient", "sysacadWriteRestClient");
            assertThat(ReflectionTestUtils.getField(context.getBean(SysacadClient.class), "restClient"))
                    .isSameAs(context.getBean("sysacadRestClient"));
            assertThat(ReflectionTestUtils.getField(context.getBean(SysacadWriteClient.class), "restClient"))
                    .isSameAs(context.getBean("sysacadWriteRestClient"));
        });
    }

    @Test
    @DisplayName("el RestClient de escritura corta por su read-timeout corto y el de lectura espera la respuesta lenta")
    void writeClientUsesShortReadTimeout() throws IOException {
        SysacadProperties properties = new SysacadProperties();
        properties.setBaseUrl(startSlowServer());
        properties.setApiKey("clave");
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setMaxRetries(0);
        properties.setWrite(shortReadTimeout(SysacadProperties.Write.defaults()));
        SysacadConfiguration configuration = new SysacadConfiguration();

        SysacadClient readClient = new SysacadClient(configuration.sysacadRestClient(properties), properties);
        SysacadWriteClient writeClient = new SysacadWriteClient(configuration.sysacadWriteRestClient(properties), properties);

        assertThat(readClient.fetchRows("HorariosComisionesCupos", ViewQuery.none(), SCHEDULE_TYPE)).isEmpty();
        assertThat(writeClient.readSchedule(9, 2023, 101)).isInstanceOf(ScheduleReadResult.ConnectionFailed.class);
    }

    private static SysacadProperties.Write shortReadTimeout(SysacadProperties.Write w) {
        return new SysacadProperties.Write(w.maxAttempts(),
                w.maxConsecutiveFailures(), w.retryBackoff(), Duration.ofMillis(300), w.inCallRetryDelay(),
                w.outageAlertAfter(), w.outageAlertRepeat(), w.maxSendsPerRun(), w.maxRequestsPerMinute(),
                w.recentSendWindow(), w.waitingVisibleBefore(), w.afterSyncMargin(), w.noCronRetryDelay(),
                w.alertMailMaxLines());
    }

    @Test
    @DisplayName("con siga.sysacad.enabled=false no se registra ningún RestClient de SysAcad")
    void disabledRegistersNothing() {
        runner.withPropertyValues("siga.sysacad.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(RestClient.class));
    }
}
