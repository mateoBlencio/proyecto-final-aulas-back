package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationSource;
import jakarta.validation.constraints.NotNull;

public record AllocateOccurrenceRequestDto(
        @NotNull Integer classroomId,
        @NotNull AllocationSource source,
        String observation
) {}
