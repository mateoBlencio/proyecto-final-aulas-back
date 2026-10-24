package ar.edu.utn.frc.siga.cameracount.service.impl;

import ar.edu.utn.frc.siga.cameracount.client.HttpCameraCountClient;
import ar.edu.utn.frc.siga.cameracount.client.RtspUrls;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraProbeResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class CameraCountServiceImpl implements CameraCountService {

    private static final int MIN_SAMPLES = 1;
    private static final int MAX_SAMPLES = 10;
    private static final String DISABLED = "El conteo por cámaras está deshabilitado en este entorno";

    // Vacío con siga.camera-count.enabled=false: el service existe igual para que los consumidores siempre lo encuentren.
    private final Optional<HttpCameraCountClient> client;

    public CameraCountServiceImpl(Optional<HttpCameraCountClient> client) {
        this.client = client;
    }

    @Override
    public CameraCountResponseDto count(String rtspUrl, Integer samples) {
        if (samples != null && (samples < MIN_SAMPLES || samples > MAX_SAMPLES)) {
            throw new IllegalArgumentException(
                    "samples debe estar entre " + MIN_SAMPLES + " y " + MAX_SAMPLES + ", pero fue " + samples);
        }
        CameraCountResponseDto count = client().count(rtspUrl, samples);
        log.debug("Conteo de {}: {} ({} de {} muestras, {} ms)", RtspUrls.mask(rtspUrl), count.status(),
                count.samplesOk(), count.samplesRequested(), count.durationMs());
        return count;
    }

    @Override
    public CameraProbeResponseDto probe(String rtspUrl) {
        CameraProbeResponseDto probe = client().probe(rtspUrl);
        log.debug("Prueba de {}: conecta={} ({} ms)", RtspUrls.mask(rtspUrl), probe.connects(),
                probe.durationMs());
        return probe;
    }

    @Override
    public CameraCountHealthResponseDto health() {
        return client().health();
    }

    private HttpCameraCountClient client() {
        return client.orElseThrow(() -> new CameraCountUnavailableException(DISABLED));
    }
}
