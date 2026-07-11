package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

/** Representación pública de un período académico (año + cuatrimestre). */
@NamedInterface("api")
public record AcademicPeriodResponseDto(
        Integer year,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate
) {
}
