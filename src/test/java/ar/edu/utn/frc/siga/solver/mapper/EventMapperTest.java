package ar.edu.utn.frc.siga.solver.mapper;

import ar.edu.utn.frc.siga.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper();

    private EventRequestDto recurringDto(String id, int dur) {
        EventRequestDto dto = new EventRequestDto();
        dto.setId(id);
        dto.setType(EventRequestDto.EventType.RECURRING);
        dto.setSubject("Física I");
        dto.setSection("1C1");
        dto.setEnrolled(68);
        dto.setStartTime(LocalTime.of(8, 0));
        dto.setDurationMinutes(dur);
        dto.setDayOfWeek(DayOfWeek.MONDAY);
        dto.setStartDate(LocalDate.of(2024, 3, 4));
        dto.setEndDate(LocalDate.of(2024, 6, 30));
        return dto;
    }

    private EventRequestDto uniqueDto(String id, LocalDate date) {
        EventRequestDto dto = new EventRequestDto();
        dto.setId(id);
        dto.setType(EventRequestDto.EventType.UNIQUE);
        dto.setSubject("Examen");
        dto.setSection("1C1");
        dto.setEnrolled(80);
        dto.setStartTime(LocalTime.of(8, 0));
        dto.setDurationMinutes(120);
        dto.setDate(date);
        return dto;
    }

    @Test
    void upEm001_recurring_allFields() {
        SolverEvent event = mapper.toEvent(recurringDto("rec-1", 90));
        assertThat(event.planningId()).isEqualTo("rec-1");
        assertThat(event.occurrenceDates()).contains(LocalDate.of(2024, 3, 4));
        assertThat(event.occurrenceDates()).allMatch(d -> d.getDayOfWeek() == DayOfWeek.MONDAY);
        assertThat(event.occurrenceDates()).noneMatch(d -> d.isAfter(LocalDate.of(2024, 6, 30)));
    }

    @Test
    void upEm002_unique_correctDate() {
        LocalDate date = LocalDate.of(2024, 7, 23);
        SolverEvent event = mapper.toEvent(uniqueDto("uni-1", date));
        assertThat(event.occurrenceDates()).containsExactly(date);
    }

    @Test
    void upEm003_duration_90min() {
        SolverEvent event = mapper.toEvent(recurringDto("e1", 90));
        assertThat(event.endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void upEm004_duration_225min() {
        SolverEvent event = mapper.toEvent(recurringDto("e1", 225));
        assertThat(event.endTime()).isEqualTo(LocalTime.of(11, 45));
    }

    @Test
    void upEm005_mixedList_correctTypes() {
        List<EventRequestDto> dtos = List.of(
                recurringDto("r1", 90),
                uniqueDto("u1", LocalDate.of(2024, 7, 1)),
                recurringDto("r2", 135)
        );
        List<SolverEvent> events = mapper.toEvents(dtos);
        assertThat(events.get(0).planningId()).isEqualTo("r1");
        assertThat(events.get(1).occurrenceDates()).containsExactly(LocalDate.of(2024, 7, 1));
        assertThat(events.get(2).planningId()).isEqualTo("r2");
    }
}
