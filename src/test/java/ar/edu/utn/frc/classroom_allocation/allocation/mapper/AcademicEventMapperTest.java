package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicEventMapperTest {

    private AcademicEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AcademicEventMapper();
    }

    // ─── RecurringEvent ──────────────────────────────────────────────────────

    @Test
    void upAem001_toDto_recurringEvent_mapsSubjectCommissionStudyPlanSpecialtyAndPeriod() {
        Specialty specialty = Specialty.builder().id(1L).specialtyCode(7).name("Ingeniería en Sistemas").build();
        StudyPlan studyPlan = StudyPlan.builder().id(1L).planCode(2008).specialty(specialty).build();
        Subject subject = Subject.builder().id(1L).code(100).name("Programación I").term("Anual").studyPlan(studyPlan).build();
        AcademicPeriod period = AcademicPeriod.builder().id(1L).year(2024).semester(1).build();
        Commission commission = Commission.builder()
                .id(1L)
                .courseCode("K1234")
                .commissionNumber(2)
                .yearLevel(1)
                .academicPeriod(period)
                .build();

        RecurringEvent event = RecurringEvent.builder()
                .id(5L)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2024, 3, 1))
                .endDate(LocalDate.of(2024, 7, 1))
                .subject(subject)
                .commission(commission)
                .build();

        AcademicEventResponseDto dto = mapper.toDto(event);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getType()).isEqualTo(EventType.RECURRING);
        assertThat(dto.getEnrolled()).isEqualTo(30);
        assertThat(dto.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(dto.getDurationMinutes()).isEqualTo(90L);
        assertThat(dto.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(dto.getStartDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(dto.getEndDate()).isEqualTo(LocalDate.of(2024, 7, 1));

        assertThat(dto.getSubjectCode()).isEqualTo(100);
        assertThat(dto.getSubjectName()).isEqualTo("Programación I");
        assertThat(dto.getSubjectTerm()).isEqualTo("Anual");
        assertThat(dto.getStudyPlanCode()).isEqualTo(2008);
        assertThat(dto.getSpecialtyCode()).isEqualTo(7);
        assertThat(dto.getSpecialtyName()).isEqualTo("Ingeniería en Sistemas");

        assertThat(dto.getCommissionCode()).isEqualTo("K1234");
        assertThat(dto.getCommissionNumber()).isEqualTo(2);
        assertThat(dto.getYearLevel()).isEqualTo(1);
        assertThat(dto.getPeriodYear()).isEqualTo(2024);
        assertThat(dto.getPeriodSemester()).isEqualTo(1);

        assertThat(dto.getDate()).isNull();
        assertThat(dto.getDescription()).isNull();
    }

    @Test
    void upAem002_toDto_recurringEvent_nullSubjectAndCommission_leavesDerivedFieldsNull() {
        RecurringEvent event = RecurringEvent.builder()
                .id(6L)
                .enrolled(15)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(60))
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startDate(LocalDate.of(2024, 3, 1))
                .build();

        AcademicEventResponseDto dto = mapper.toDto(event);

        assertThat(dto.getType()).isEqualTo(EventType.RECURRING);
        assertThat(dto.getSubjectCode()).isNull();
        assertThat(dto.getSubjectName()).isNull();
        assertThat(dto.getSubjectTerm()).isNull();
        assertThat(dto.getStudyPlanCode()).isNull();
        assertThat(dto.getSpecialtyCode()).isNull();
        assertThat(dto.getSpecialtyName()).isNull();
        assertThat(dto.getCommissionCode()).isNull();
        assertThat(dto.getCommissionNumber()).isNull();
        assertThat(dto.getYearLevel()).isNull();
        assertThat(dto.getPeriodYear()).isNull();
        assertThat(dto.getPeriodSemester()).isNull();
    }

    // ─── UniqueEvent ─────────────────────────────────────────────────────────

    @Test
    void upAem003_toDto_uniqueEvent_mapsDateAndDescription() {
        UniqueEvent event = UniqueEvent.builder()
                .id(7L)
                .enrolled(20)
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(45))
                .date(LocalDate.of(2024, 5, 1))
                .description("Charla informativa")
                .build();

        AcademicEventResponseDto dto = mapper.toDto(event);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getType()).isEqualTo(EventType.UNIQUE_EVENT);
        assertThat(dto.getEnrolled()).isEqualTo(20);
        assertThat(dto.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.getDurationMinutes()).isEqualTo(45L);
        assertThat(dto.getDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(dto.getDescription()).isEqualTo("Charla informativa");

        assertThat(dto.getDayOfWeek()).isNull();
        assertThat(dto.getSubjectName()).isNull();
        assertThat(dto.getCommissionCode()).isNull();
    }
}
