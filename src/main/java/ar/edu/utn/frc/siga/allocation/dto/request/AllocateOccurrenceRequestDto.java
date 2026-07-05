package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

public record AllocateOccurrenceRequestDto(
        @NotNull Integer classroomId,
        String observation
) {}
