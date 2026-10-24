package ar.edu.utn.frc.siga.cameracount.api;

public interface CameraCountService {

    /**
     * @param samples cantidad de fotogramas a promediar, entre 1 y 10; null usa el default del servicio.
     */
    CameraCount count(String rtspUrl, Integer samples);

    CameraProbe probe(String rtspUrl);

    CameraCountHealth health();
}
