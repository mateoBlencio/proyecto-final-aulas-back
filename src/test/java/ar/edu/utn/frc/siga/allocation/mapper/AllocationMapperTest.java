package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSummaryDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationMapperTest {

    private AllocationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AllocationMapper(new AcademicEventMapper(), new ClassroomMapper());
    }

    private Classroom classroom() {
        Building building = Building.builder().id(1).name("Edificio Central").build();
        return Classroom.builder()
                .id(10)
                .roomNumber("101")
                .capacity(40)
                .building(building)
                .build();
    }

    private RecurringEvent recurringEvent() {
        Subject subject = Subject.builder().id(1L).code(100).name("Programación I").build();
        Commission commission = Commission.builder().id(1L).courseCode("K1234").commissionNumber(1).build();
        return RecurringEvent.builder()
                .id(5L)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2024, 3, 1))
                .subject(subject)
                .commission(commission)
                .build();
    }

    private UniqueEvent uniqueEvent() {
        return UniqueEvent.builder()
                .id(6L)
                .enrolled(20)
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(60))
                .date(LocalDate.of(2024, 5, 1))
                .description("Evento especial")
                .build();
    }

    // ─── toDto ───────────────────────────────────────────────────────────────

    @Test
    void upAm001_toDto_mapsOccurrenceEventAndClassroom() {
        RecurringEvent event = recurringEvent();
        Occurrence occurrence = Occurrence.builder()
                .id(50L)
                .event(event)
                .date(LocalDate.of(2024, 3, 4))
                .status(OccurrenceStatus.SCHEDULED)
                .build();
        Classroom classroom = classroom();
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0);

        Allocation allocation = Allocation.builder()
                .id(1L)
                .occurrence(occurrence)
                .classroom(classroom)
                .source(AllocationSource.MANUAL)
                .createdAt(createdAt)
                .observation("obs")
                .build();

        AllocationResponseDto dto = mapper.toDto(allocation);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
        assertThat(dto.getObservation()).isEqualTo("obs");

        assertThat(dto.getOccurrence()).isNotNull();
        assertThat(dto.getOccurrence().getId()).isEqualTo(50L);
        assertThat(dto.getOccurrence().getEventId()).isEqualTo(5L);
        assertThat(dto.getOccurrence().getDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(dto.getOccurrence().getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(dto.getOccurrence().getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(dto.getOccurrence().getEndTime()).isEqualTo(LocalTime.of(9, 30));

        assertThat(dto.getEvent()).isNotNull();
        assertThat(dto.getEvent().getId()).isEqualTo(5L);
        assertThat(dto.getEvent().getType()).isEqualTo(EventType.RECURRING);
        assertThat(dto.getEvent().getSubjectName()).isEqualTo("Programación I");

        assertThat(dto.getClassroom()).isNotNull();
        assertThat(dto.getClassroom().getId()).isEqualTo(10);
        assertThat(dto.getClassroom().getRoomNumber()).isEqualTo("101");
    }

    // ─── toSummaryDto ────────────────────────────────────────────────────────

    @Test
    void upAm002_toSummaryDto_recurringEvent_populatesSubjectAndSection() {
        RecurringEvent event = recurringEvent();
        Occurrence occurrence = Occurrence.builder()
                .id(50L)
                .event(event)
                .date(LocalDate.of(2024, 3, 4))
                .status(OccurrenceStatus.SCHEDULED)
                .build();
        Classroom classroom = classroom();

        Allocation allocation = Allocation.builder()
                .id(2L)
                .occurrence(occurrence)
                .classroom(classroom)
                .source(AllocationSource.AUTOMATIC)
                .createdAt(LocalDateTime.now())
                .build();

        AllocationSummaryDto summary = mapper.toSummaryDto(allocation);

        assertThat(summary.getId()).isEqualTo(2L);
        assertThat(summary.getEventType()).isEqualTo(EventType.RECURRING);
        assertThat(summary.getSubject()).isEqualTo("Programación I");
        assertThat(summary.getSection()).isEqualTo("K1234");
        assertThat(summary.getClassroomId()).isEqualTo(10);
        assertThat(summary.getClassroomName()).isEqualTo("101");
        assertThat(summary.getBuildingId()).isEqualTo(1);
        assertThat(summary.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(summary.getEndTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(summary.getEnrolled()).isEqualTo(30);
        assertThat(summary.getCapacity()).isEqualTo(40);
    }

    @Test
    void upAm003_toSummaryDto_uniqueEvent_leavesSubjectAndSectionNull() {
        UniqueEvent event = uniqueEvent();
        Occurrence occurrence = Occurrence.builder()
                .id(51L)
                .event(event)
                .date(LocalDate.of(2024, 5, 1))
                .status(OccurrenceStatus.SCHEDULED)
                .build();
        Classroom classroom = classroom();

        Allocation allocation = Allocation.builder()
                .id(3L)
                .occurrence(occurrence)
                .classroom(classroom)
                .source(AllocationSource.IMPORTED)
                .createdAt(LocalDateTime.now())
                .build();

        AllocationSummaryDto summary = mapper.toSummaryDto(allocation);

        assertThat(summary.getEventType()).isEqualTo(EventType.UNIQUE_EVENT);
        assertThat(summary.getSubject()).isNull();
        assertThat(summary.getSection()).isNull();
        assertThat(summary.getEnrolled()).isEqualTo(20);
        assertThat(summary.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(summary.getEndTime()).isEqualTo(LocalTime.of(11, 0));
    }
}
