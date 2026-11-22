package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.util.List;

/** Start dates of the active academic periods, provided by the module that owns them. */
@NamedInterface("api")
public interface AcademicCycleCalendar {

    List<PeriodStart> activePeriodStarts();

    record PeriodStart(Integer year, LocalDate startDate) {
    }
}
