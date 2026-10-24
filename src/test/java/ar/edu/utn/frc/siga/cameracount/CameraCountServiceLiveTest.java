package ar.edu.utn.frc.siga.cameracount;

import ar.edu.utn.frc.siga.cameracount.config.CameraCountConfiguration;
import ar.edu.utn.frc.siga.cameracount.config.CameraCountProperties;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import ar.edu.utn.frc.siga.cameracount.service.impl.CameraCountServiceImpl;
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
        CameraCountHealthResponseDto health = liveService().health();

        assertThat(health.version()).isNotBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CONTEO_TEST_RTSP_URL", matches = ".+")
    @DisplayName("count(url, 3) contra el servicio real devuelve estado y 3 muestras pedidas (requiere CONTEO_TEST_RTSP_URL)")
    void countReturnsRequestedSamples() {
        CameraCountResponseDto count = liveService().count(System.getenv("CONTEO_TEST_RTSP_URL"), 3);

        assertThat(count.status()).isNotNull();
        assertThat(count.samplesRequested()).isEqualTo(3);
        assertThat(count.samplesOk()).isBetween(0, 3);
    }
}
