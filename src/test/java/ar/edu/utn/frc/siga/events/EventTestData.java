package ar.edu.utn.frc.siga.events;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public final class EventTestData {

    private EventTestData() {
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

    public static OccurrenceSlotDto occurrenceSlot(Long id, AcademicEvent event, LocalDate date, OccurrenceStatus status) {
        return new OccurrenceSlotDto(id, event.getId(), date, event.getStartTime(), event.endTime(), status, event.getEnrolled());
    }

    public static SubjectResponseDto subjectResponseDto(Long id) {
        return new SubjectResponseDto(id, 100, "Materia de prueba", "anual", null);
    }

    public static CommissionResponseDto commissionResponseDto(Long id) {
        return new CommissionResponseDto(id, "K1234", null);
    }
}
