package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AllocateFromDateRequestDto(
        @NotNull Long recurringEventId,
        @NotNull LocalDate fromDate,
        @NotNull Integer classroomId,
        String observation
) {}
