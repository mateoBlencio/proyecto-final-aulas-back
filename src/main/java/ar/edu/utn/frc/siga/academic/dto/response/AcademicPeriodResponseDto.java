package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record AcademicPeriodResponseDto(
        Long id,
        Integer year,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate recessStart,
        LocalDate recessEnd,
        boolean enabled,
        UnavailableReason unavailableReason
) {
    public AcademicPeriodResponseDto(Long id, Integer year, Integer semester, LocalDate startDate,
            LocalDate endDate, LocalDate recessStart, LocalDate recessEnd) {
        this(id, year, semester, startDate, endDate, recessStart, recessEnd, true, null);
    }

    public AcademicPeriodResponseDto(Integer year, Integer semester, LocalDate startDate, LocalDate endDate) {
        this(null, year, semester, startDate, endDate, null, null, true, null);
    }
}
