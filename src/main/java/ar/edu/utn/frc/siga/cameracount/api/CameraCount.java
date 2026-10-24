package ar.edu.utn.frc.siga.cameracount.api;

import java.time.Duration;
import java.time.Instant;

/**
 * {@code people} y {@code estimate} son null cuando {@code status} es {@link CameraCountStatus#NO_SIGNAL}.
 */
public record CameraCount(
        CameraCountStatus status,
        Integer people,
        Double estimate,
        int samplesRequested,
        int samplesOk,
        Instant measuredAt,
        Duration duration
) {}
