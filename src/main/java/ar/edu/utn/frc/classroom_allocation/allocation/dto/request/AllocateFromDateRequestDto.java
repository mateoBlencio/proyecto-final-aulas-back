package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationSource;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AllocateFromDateRequestDto(
        @NotNull Long recurringEventId,
        @NotNull LocalDate fromDate,
        @NotNull Integer classroomId,
        @NotNull AllocationSource source,
        String observation
) {}
