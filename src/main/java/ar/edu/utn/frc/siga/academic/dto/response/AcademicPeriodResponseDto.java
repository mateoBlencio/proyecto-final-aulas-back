package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record AcademicPeriodResponseDto(
        Integer year,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate recessStart,
        LocalDate recessEnd
) {
    public AcademicPeriodResponseDto(Integer year, Integer semester, LocalDate startDate, LocalDate endDate) {
        this(year, semester, startDate, endDate, null, null);
    }
}
