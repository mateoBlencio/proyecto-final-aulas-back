package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.audit.AcademicCycleCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Gives {@code audit} the start dates of the active periods without exposing the academic DTOs. */
@Component
@RequiredArgsConstructor
class AcademicPeriodCycleCalendar implements AcademicCycleCalendar {

    private final AcademicPeriodService academicPeriodService;

    @Override
    public List<PeriodStart> activePeriodStarts() {
        return academicPeriodService.findActive().stream()
                .map(period -> new PeriodStart(period.year(), period.startDate()))
                .toList();
    }
}
