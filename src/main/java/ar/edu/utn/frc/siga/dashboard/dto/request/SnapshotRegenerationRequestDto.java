package ar.edu.utn.frc.siga.dashboard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SnapshotRegenerationRequestDto(@NotBlank @Size(max = 500) String reason) {
}
