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
    CameraCountResponseDto count(String rtspUrl, Integer samples);

    CameraProbeResponseDto probe(String rtspUrl);

    CameraCountHealthResponseDto health();
}
