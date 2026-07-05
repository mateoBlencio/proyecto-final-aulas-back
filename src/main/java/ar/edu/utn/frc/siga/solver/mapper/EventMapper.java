package ar.edu.utn.frc.siga.solver.mapper;

import ar.edu.utn.frc.siga.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.siga.solver.dto.response.EventSummaryDto;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class EventMapper {

    public List<SolverEvent> toEvents(List<EventRequestDto> dtos) {
        return dtos.stream().map(this::toEvent).toList();
    }

    public SolverEvent toEvent(EventRequestDto dto) {
        LocalTime endTime = dto.getStartTime().plus(Duration.ofMinutes(dto.getDurationMinutes()));
        Set<LocalDate> occurrenceDates = switch (dto.getType()) {
            case RECURRING -> recurringDates(dto.getDayOfWeek(), dto.getStartDate(), dto.getEndDate());
            case UNIQUE -> Set.of(dto.getDate());
        };
        return new SolverEvent(dto.getId(), dto.getEnrolled(), dto.getStartTime(), endTime, occurrenceDates);
    }

    private Set<LocalDate> recurringDates(DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        LocalDate end = endDate != null ? endDate : startDate.plusYears(1);
        LocalDate current = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        while (!current.isAfter(end)) {
            dates.add(current);
            current = current.plusWeeks(1);
        }
        return dates;
    }

    public EventSummaryDto toSummary(EventRequestDto dto) {
        return EventSummaryDto.builder()
                .id(dto.getId())
                .type(dto.getType())
                .subject(dto.getSubject())
                .section(dto.getSection())
                .enrolled(dto.getEnrolled())
                .startTime(dto.getStartTime())
                .durationMinutes(dto.getDurationMinutes())
                .dayOfWeek(dto.getDayOfWeek())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .date(dto.getDate())
                .build();
    }
}
