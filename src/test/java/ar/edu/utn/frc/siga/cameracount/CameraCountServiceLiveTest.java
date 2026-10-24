package ar.edu.utn.frc.siga.cameracount;

import ar.edu.utn.frc.siga.cameracount.api.CameraCount;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountHealth;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountService;
import ar.edu.utn.frc.siga.cameracount.internal.config.CameraCountConfiguration;
import ar.edu.utn.frc.siga.cameracount.internal.config.CameraCountProperties;
import ar.edu.utn.frc.siga.cameracount.internal.service.CameraCountServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CONTEO_URL", matches = ".+")
@DisplayName("CameraCountService contra el servicio real (requiere CONTEO_URL y CONTEO_API_KEY)")
class CameraCountServiceLiveTest {

    private static CameraCountService liveService() {
        CameraCountProperties properties = new CameraCountProperties();
        properties.setUrl(System.getenv("CONTEO_URL"));
        properties.setApiKey(System.getenv().getOrDefault("CONTEO_API_KEY", ""));
        properties.setReadTimeout(Duration.ofSeconds(90));
        return new CameraCountServiceImpl(Optional.of(new CameraCountConfiguration().httpCameraCountClient(properties)));
    }

    @Test
    @DisplayName("health() contra el servicio real devuelve la versión")
    void healthReturnsVersion() {
        CameraCountHealth health = liveService().health();

        assertThat(health.version()).isNotBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CONTEO_TEST_RTSP_URL", matches = ".+")
    @DisplayName("count(url, 3) contra el servicio real devuelve estado y 3 muestras pedidas (requiere CONTEO_TEST_RTSP_URL)")
    void countReturnsRequestedSamples() {
        CameraCount count = liveService().count(System.getenv("CONTEO_TEST_RTSP_URL"), 3);

        assertThat(count.status()).isNotNull();
        assertThat(count.samplesRequested()).isEqualTo(3);
        assertThat(count.samplesOk()).isBetween(0, 3);
    }
}
