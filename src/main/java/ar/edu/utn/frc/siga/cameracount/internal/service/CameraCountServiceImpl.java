package ar.edu.utn.frc.siga.cameracount.internal.service;

import ar.edu.utn.frc.siga.cameracount.api.CameraCount;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountHealth;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountService;
import ar.edu.utn.frc.siga.cameracount.api.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.api.CameraProbe;
import ar.edu.utn.frc.siga.cameracount.internal.client.HttpCameraCountClient;
import ar.edu.utn.frc.siga.cameracount.internal.client.RtspUrls;
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
    public CameraCount count(String rtspUrl, Integer samples) {
        if (samples != null && (samples < MIN_SAMPLES || samples > MAX_SAMPLES)) {
            throw new IllegalArgumentException(
                    "samples debe estar entre " + MIN_SAMPLES + " y " + MAX_SAMPLES + ", pero fue " + samples);
        }
        CameraCount count = client().count(rtspUrl, samples);
        log.debug("Conteo de {}: {} ({} de {} muestras, {} ms)", RtspUrls.mask(rtspUrl), count.status(),
                count.samplesOk(), count.samplesRequested(), count.duration().toMillis());
        return count;
    }

    @Override
    public CameraProbe probe(String rtspUrl) {
        CameraProbe probe = client().probe(rtspUrl);
        log.debug("Prueba de {}: conecta={} ({} ms)", RtspUrls.mask(rtspUrl), probe.connects(),
                probe.duration().toMillis());
        return probe;
    }

    @Override
    public CameraCountHealth health() {
        return client().health();
    }

    private HttpCameraCountClient client() {
        return client.orElseThrow(() -> new CameraCountUnavailableException(DISABLED));
    }
}
