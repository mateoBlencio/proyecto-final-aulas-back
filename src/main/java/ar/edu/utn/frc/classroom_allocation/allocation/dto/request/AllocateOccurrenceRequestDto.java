package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

public record AllocateOccurrenceRequestDto(
        @NotNull Integer classroomId,
        String observation
) {}
