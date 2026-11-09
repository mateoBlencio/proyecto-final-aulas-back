package ar.edu.utn.frc.siga.sysacad.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TestCommissionMessageRequestDto(
        @NotNull Integer specialty,
        @NotNull Integer plan,
        @NotNull Integer subject,
        @NotNull Integer commission,
        @NotNull Integer year,
        @NotNull LocalDate classDate,
        @NotNull Integer building,
        @NotNull Integer room
) {}
