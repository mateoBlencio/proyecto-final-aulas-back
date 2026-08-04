package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fixture compartida para los tests unitarios nuevos del módulo {@code allocation}. Los tests
 * existentes (AllocationServiceImplTest, AllocationProblemServiceImplTest,
 * AutoAllocationServiceImplTest) construyen sus datos inline y no usan esta clase.
 */
public final class AllocationTestData {

    private AllocationTestData() {
    }

    public static RecurringEvent recurringEvent(Long id, DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(dayOfWeek)
                .startDate(startDate)
                .endDate(endDate)
                .subjectId(1L)
                .commissionId(1L)
                .build();
    }

    public static RecurringEvent recurringEvent(Long id, LocalTime startTime, Duration duration) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(30)
                .startTime(startTime)
                .duration(duration)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2026, 1, 5))
                .endDate(LocalDate.of(2026, 6, 30))
                .subjectId(1L)
                .commissionId(1L)
                .build();
    }

    public static UniqueEvent uniqueEvent(Long id, LocalDate date, LocalTime startTime, Duration duration) {
        return UniqueEvent.builder()
                .id(id)
                .enrolled(20)
                .startTime(startTime)
                .duration(duration)
                .date(date)
                .description("evento de prueba")
                .kind(UniqueEventKind.EXAMEN_FINAL)
                .subjectId(1L)
                .commissionId(1L)
                .build();
    }

    public static Occurrence occurrence(Long id, AcademicEvent event, LocalDate date, OccurrenceStatus status) {
        return Occurrence.builder()
                .id(id)
                .event(event)
                .date(date)
                .status(status)
                .build();
    }

    public static SubjectResponseDto subjectResponseDto(Long id) {
        return new SubjectResponseDto(id, 100, "Materia de prueba", "anual", null);
    }

    public static CommissionResponseDto commissionResponseDto(Long id) {
        return new CommissionResponseDto(id, "K1234", 1, 1, null);
    }
}
