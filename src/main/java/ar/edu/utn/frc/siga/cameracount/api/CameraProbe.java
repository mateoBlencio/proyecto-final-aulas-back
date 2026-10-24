package ar.edu.utn.frc.siga.cameracount.api;

import java.time.Duration;

public record CameraProbe(
        boolean connects,
        Integer width,
        Integer height,
        CameraProbeFailure failure,
        Duration duration
) {}
