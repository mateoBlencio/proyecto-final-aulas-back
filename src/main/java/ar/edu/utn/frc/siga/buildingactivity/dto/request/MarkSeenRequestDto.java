package ar.edu.utn.frc.siga.buildingactivity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkSeenRequestDto(@NotNull @Positive Long lastSeenId) {
}
