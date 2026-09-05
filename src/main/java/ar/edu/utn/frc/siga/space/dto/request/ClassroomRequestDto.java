package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClassroomRequestDto(
    @NotNull Integer roomNumber,
    @NotNull @Positive Integer capacity,
    @NotNull Long classroomTypeId,
    @NotNull Long buildingId
) {
}
