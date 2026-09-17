package ar.edu.utn.frc.siga.academic.event;

import java.time.LocalDate;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record AcademicPeriodChanged(
        Long periodId,
        Integer year,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate recessStart,
        LocalDate recessEnd
) {
}
