package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record AllocateFromDateRequestDto(
        @NotNull Long recurringEventId,
        @NotNull LocalDate fromDate,
        @NotNull Integer classroomId,
        String observation
) {}
