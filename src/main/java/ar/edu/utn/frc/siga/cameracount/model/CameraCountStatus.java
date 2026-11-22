package ar.edu.utn.frc.siga.cameracount.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum CameraCountStatus {
    OK,
    NO_SIGNAL
}
