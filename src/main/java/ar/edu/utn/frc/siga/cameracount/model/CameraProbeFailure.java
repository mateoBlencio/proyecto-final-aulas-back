package ar.edu.utn.frc.siga.cameracount.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum CameraProbeFailure {
    NOT_CONNECTING,
    NO_FRAME,
    TIMEOUT
}
