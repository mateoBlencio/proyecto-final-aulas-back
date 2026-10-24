package ar.edu.utn.frc.siga.cameracount.internal.service;

import ar.edu.utn.frc.siga.cameracount.api.CameraCount;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountHealth;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.api.CameraProbe;
import ar.edu.utn.frc.siga.cameracount.api.InvalidCameraUrlException;
import ar.edu.utn.frc.siga.cameracount.internal.client.HttpCameraCountClient;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CameraCountServiceImpl: validación, delegación y logs del conteo por cámaras")
class CameraCountServiceImplTest {

    private static final String RTSP = "rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101";
    private static final String MASKED_TARGET = "rtsp://***@172.16.1.10:554/...";
    private static final String DISABLED_DETAIL = "El conteo por cámaras está deshabilitado en este entorno";

    private static final CameraCount COUNT = new CameraCount(CameraCountStatus.OK, 3, 3.4086, 5, 5,
            Instant.parse("2026-10-24T03:43:43Z"), Duration.ofMillis(24117));
    private static final CameraProbe PROBE = new CameraProbe(true, 1920, 1080, null, Duration.ofMillis(850));
    private static final CameraCountHealth HEALTH = new CameraCountHealth(true, "yolo", "0.1.0");

    @Mock
    private HttpCameraCountClient client;

    private CameraCountServiceImpl service;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger serviceLogger;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        service = new CameraCountServiceImpl(Optional.of(client));

        serviceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CameraCountServiceImpl.class);
        previousLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
        serviceLogger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("sin cliente (conteo deshabilitado): count lanza Unavailable (503) con el detalle de deshabilitado")
    void disabledCountThrowsUnavailable() {
        CameraCountServiceImpl disabled = new CameraCountServiceImpl(Optional.empty());

        assertThatThrownBy(() -> disabled.count(RTSP, 5))
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .hasMessage(DISABLED_DETAIL)
                .isInstanceOfSatisfying(CameraCountUnavailableException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("sin cliente (conteo deshabilitado): probe lanza Unavailable con el detalle de deshabilitado")
    void disabledProbeThrowsUnavailable() {
        CameraCountServiceImpl disabled = new CameraCountServiceImpl(Optional.empty());

        assertThatThrownBy(() -> disabled.probe(RTSP))
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .hasMessage(DISABLED_DETAIL);
    }

    @Test
    @DisplayName("sin cliente (conteo deshabilitado): health lanza Unavailable con el detalle de deshabilitado")
    void disabledHealthThrowsUnavailable() {
        CameraCountServiceImpl disabled = new CameraCountServiceImpl(Optional.empty());

        assertThatThrownBy(disabled::health)
                .isExactlyInstanceOf(CameraCountUnavailableException.class)
                .hasMessage(DISABLED_DETAIL);
    }

    @Test
    @DisplayName("con cliente: count delega con la URL y los samples y devuelve lo del cliente")
    void countDelegatesToClient() {
        when(client.count(RTSP, 5)).thenReturn(COUNT);

        assertThat(service.count(RTSP, 5)).isSameAs(COUNT);

        verify(client).count(RTSP, 5);
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("con cliente: probe delega con la URL y devuelve lo del cliente")
    void probeDelegatesToClient() {
        when(client.probe(RTSP)).thenReturn(PROBE);

        assertThat(service.probe(RTSP)).isSameAs(PROBE);

        verify(client).probe(RTSP);
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("con cliente: health delega y devuelve lo del cliente")
    void healthDelegatesToClient() {
        when(client.health()).thenReturn(HEALTH);

        assertThat(service.health()).isSameAs(HEALTH);

        verify(client).health();
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest(name = "samples={0} es válido y se delega con ese valor")
    @NullSource
    @ValueSource(ints = {1, 10})
    @DisplayName("samples null, 1 y 10 (default y extremos del rango): delega con el mismo valor")
    void validSamplesAreDelegatedUnchanged(Integer samples) {
        when(client.count(RTSP, samples)).thenReturn(COUNT);

        assertThat(service.count(RTSP, samples)).isSameAs(COUNT);

        verify(client).count(RTSP, samples);
    }

    @ParameterizedTest(name = "samples={0} lanza IllegalArgumentException sin llamar al cliente")
    @ValueSource(ints = {0, 11})
    @DisplayName("samples justo fuera del rango (0 y 11): IllegalArgumentException y el cliente no se toca")
    void samplesOutOfRangeFailsWithoutCallingClient(int samples) {
        assertThatThrownBy(() -> service.count(RTSP, samples))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 y 10")
                .hasMessageContaining(String.valueOf(samples));

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("una excepción del cliente sale tal cual y no deja log de éxito")
    void clientExceptionsPropagateUnchanged() {
        InvalidCameraUrlException failure = new InvalidCameraUrlException("rechazada");
        when(client.count(RTSP, 5)).thenThrow(failure);

        assertThatThrownBy(() -> service.count(RTSP, 5)).isSameAs(failure);

        assertThat(logs.list).isEmpty();
    }

    @Test
    @DisplayName("el log de count lleva la URL enmascarada y los datos del conteo, sin usuario ni clave")
    void countLogMasksUrl() {
        when(client.count(RTSP, 5)).thenReturn(COUNT);

        service.count(RTSP, 5);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage())
                    .contains(MASKED_TARGET, "OK", "5 de 5", "24117 ms")
                    .doesNotContain("s3cr3t", "admin");
        });
    }

    @Test
    @DisplayName("el log de probe lleva la URL enmascarada y el resultado, sin usuario ni clave")
    void probeLogMasksUrl() {
        when(client.probe(RTSP)).thenReturn(PROBE);

        service.probe(RTSP);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage())
                    .contains(MASKED_TARGET, "conecta=true", "850 ms")
                    .doesNotContain("s3cr3t", "admin");
        });
    }
}
