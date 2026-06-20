package ar.edu.utn.frc.classroom_allocation.solver.mapper;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
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
        AcademicEvent event = mapper.toEvent(recurringDto("rec-1", 90));
        assertThat(event).isInstanceOf(RecurringEvent.class);
        RecurringEvent rec = (RecurringEvent) event;
        assertThat(rec.getPlanningId()).isEqualTo("rec-1");
        assertThat(rec.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(rec.getStartDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(rec.getEndDate()).isEqualTo(LocalDate.of(2024, 6, 30));
        assertThat(rec.getSubject()).isEqualTo("Física I");
        assertThat(rec.getSection()).isEqualTo("1C1");
    }

    @Test
    void upEm002_unique_correctDate() {
        LocalDate date = LocalDate.of(2024, 7, 23);
        AcademicEvent event = mapper.toEvent(uniqueDto("uni-1", date));
        assertThat(event).isInstanceOf(UniqueEvent.class);
        UniqueEvent uni = (UniqueEvent) event;
        assertThat(uni.getDate()).isEqualTo(date);
    }

    @Test
    void upEm003_duration_90min() {
        AcademicEvent event = mapper.toEvent(recurringDto("e1", 90));
        assertThat(event.endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void upEm004_duration_225min() {
        AcademicEvent event = mapper.toEvent(recurringDto("e1", 225));
        assertThat(event.endTime()).isEqualTo(LocalTime.of(11, 45));
    }

    @Test
    void upEm005_mixedList_correctTypes() {
        List<EventRequestDto> dtos = List.of(
                recurringDto("r1", 90),
                uniqueDto("u1", LocalDate.of(2024, 7, 1)),
                recurringDto("r2", 135)
        );
        List<AcademicEvent> events = mapper.toEvents(dtos);
        assertThat(events.get(0)).isInstanceOf(RecurringEvent.class);
        assertThat(events.get(1)).isInstanceOf(UniqueEvent.class);
        assertThat(events.get(2)).isInstanceOf(RecurringEvent.class);
    }
}
