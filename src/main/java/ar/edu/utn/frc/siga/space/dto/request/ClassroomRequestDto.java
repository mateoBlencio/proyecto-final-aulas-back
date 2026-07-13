package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos de entrada para crear o actualizar un aula.
 */
public record ClassroomRequestDto(
    @NotBlank String roomNumber,
    @NotNull @Positive Integer capacity,
    @NotNull Integer floor,
    @NotNull Integer classroomTypeId,
    @NotNull Boolean available,
    @NotNull Integer buildingId
) {
}
