package ar.edu.utn.frc.siga.cameracount.dto.response;

import ar.edu.utn.frc.siga.cameracount.model.CameraProbeFailure;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CameraProbeResponseDto(
        boolean connects,
        Integer width,
        Integer height,
        CameraProbeFailure failure,
        long durationMs
) {}
