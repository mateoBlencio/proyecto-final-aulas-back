package PF.classroom_allocation.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClassroomRequestDTO(
    @NotBlank String roomNumber,
    @NotNull @Positive Integer capacity,
    @NotNull Integer floor,
    @NotNull Integer classroomTypeId,
    @NotNull Boolean available,
    @NotNull Integer buildingId
) {
}
