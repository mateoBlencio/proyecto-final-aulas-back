package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignOccurrenceRequestDto(
        @NotNull Integer classroomId,
        @NotBlank String assignedBy,
        String observation
) {}
