package ar.edu.utn.frc.siga.academic.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateAcademicPeriodRequestDto(
        LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalDate recessStart,
        LocalDate recessEnd
) {
}
