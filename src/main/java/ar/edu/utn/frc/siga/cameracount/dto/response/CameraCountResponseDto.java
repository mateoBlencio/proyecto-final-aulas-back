package ar.edu.utn.frc.siga.cameracount.dto.response;

import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/**
 * {@code people} y {@code estimate} son null cuando {@code status} es {@link CameraCountStatus#NO_SIGNAL}.
 */
@NamedInterface("api")
public record CameraCountResponseDto(
        CameraCountStatus status,
        Integer people,
        Double estimate,
        int samplesRequested,
        int samplesOk,
        Instant measuredAt,
        long durationMs
) {}
