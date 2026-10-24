package ar.edu.utn.frc.siga.cameracount.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CameraCountHealthResponseDto(boolean ready, String estimator, String version) {}
