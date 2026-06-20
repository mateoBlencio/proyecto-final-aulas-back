package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignFromDateRequestDto(
        @NotNull Long recurringEventId,
        @NotNull LocalDate fromDate,
        @NotNull Integer classroomId,
        @NotBlank String assignedBy,
        String observation
) {}
