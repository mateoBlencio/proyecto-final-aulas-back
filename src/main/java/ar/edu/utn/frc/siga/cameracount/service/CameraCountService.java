package ar.edu.utn.frc.siga.cameracount.service;

import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraProbeResponseDto;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface CameraCountService {

    /**
     * @param samples cantidad de fotogramas a promediar, entre 1 y 10; null usa el default del servicio.
     */
    default CameraCountResponseDto count(String rtspUrl, Integer samples) {
        return count(rtspUrl, samples, null);
    }

    /**
     * @param requestId id para cruzar logs con el servicio de conteo (header X-Request-Id); si no cumple
     *                  {@code [A-Za-z0-9._-]{1,64}} no se envía.
     */
    CameraCountResponseDto count(String rtspUrl, Integer samples, String requestId);

    default CameraProbeResponseDto probe(String rtspUrl) {
        return probe(rtspUrl, null);
    }

    CameraProbeResponseDto probe(String rtspUrl, String requestId);

    CameraCountHealthResponseDto health();
}
