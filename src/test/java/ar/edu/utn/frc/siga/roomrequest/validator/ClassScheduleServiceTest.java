package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassScheduleService")
class ClassScheduleServiceTest {

    @Mock
    private AcademicEventService academicEventService;

    private ClassScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ClassScheduleService(academicEventService);
    }

    private static RecurringEventResponseDto recurring(long id, DayOfWeek day, LocalTime start, long minutes) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, start, minutes, day,
                LocalDate.now(), LocalDate.now().plusMonths(3), null, null);
    }

    @Test
    @DisplayName("requireClassDay: día que la comisión no dicta → rechazado")
    void dayNotTaught() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L))
                .thenReturn(List.of(recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        assertThatThrownBy(() -> service.requireClassDay(1L, 9L, DayOfWeek.MONDAY, null))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no dicta clase");
    }

    @Test
    @DisplayName("requireClassDay: más de una clase el mismo día → rechazado, no deriva un horario al azar")
    void ambiguousDay() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        assertThatThrownBy(() -> service.requireClassDay(1L, 9L, DayOfWeek.MONDAY, null))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("más de una clase")
                .hasMessageContaining("08:00 a 10:00")
                .hasMessageContaining("14:00 a 16:00");
    }

    @Test
    @DisplayName("requireClassDay: un único bloque → devuelve ese slot")
    void singleBlock() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L))
                .thenReturn(List.of(recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        ClassSlot slot = service.requireClassDay(1L, 9L, DayOfWeek.TUESDAY, null);

        assertThat(slot.recurringEventId()).isEqualTo(100L);
        assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("requireClassDay: cursada anual cargada como un evento por cuatrimestre → una sola franja")
    void annualSplitCountsAsOne() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120),
                recurring(101L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        ClassSlot slot = service.requireClassDay(1L, 9L, DayOfWeek.TUESDAY, null);

        assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("distinctSlots: colapsa la cursada anual partida, pero no dos bloques distintos del mismo día")
    void distinctSlotsCollapsesOnlyIdenticalHours() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120),
                recurring(101L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120),
                recurring(102L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(103L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        assertThat(service.distinctSlots(1L, 9L))
                .extracting(ClassSlot::dayOfWeek, ClassSlot::startTime)
                .containsExactly(
                        tuple(DayOfWeek.TUESDAY, LocalTime.of(18, 0)),
                        tuple(DayOfWeek.MONDAY, LocalTime.of(8, 0)),
                        tuple(DayOfWeek.MONDAY, LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("requireClassDate: resuelve el slot por el evento de la ocurrencia, no por el día")
    void dateResolvesByOccurrenceEvent() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 101L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(14, 0), LocalTime.of(16, 0), null, null, null)));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        ClassSlot slot = service.requireClassDate(1L, 9L, monday, null);

        assertThat(slot.recurringEventId()).isEqualTo(101L);
        assertThat(slot.startTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("requireClassDate: dos clases en esa fecha → rechazado, no elige una al azar")
    void ambiguousDate() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 100L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(8, 0), LocalTime.of(10, 0), null, null, null),
                new OccurrenceResponseDto(2L, 101L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(14, 0), LocalTime.of(16, 0), null, null, null)));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        assertThatThrownBy(() -> service.requireClassDate(1L, 9L, monday, null))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("más de una clase")
                .hasMessageContaining("08:00 a 10:00")
                .hasMessageContaining("Elegí el horario");
    }

    @Test
    @DisplayName("requireClassDate: dos tramos de la misma franja en esa fecha → no es ambiguo, deriva el horario")
    void annualSplitOnDateIsNotAmbiguous() {
        LocalDate tuesday = LocalDate.of(2026, 9, 8);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 100L, tuesday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(18, 0), LocalTime.of(20, 0), null, null, null),
                new OccurrenceResponseDto(2L, 101L, tuesday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(18, 0), LocalTime.of(20, 0), null, null, null)));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120),
                recurring(101L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        ClassSlot slot = service.requireClassDate(1L, 9L, tuesday, null);

        assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("requireClassDate: fecha sin clase → rechazado")
    void dateNotTaught() {
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireClassDate(1L, 9L, LocalDate.of(2026, 9, 7), null))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no tiene clase");
    }

    @Test
    @DisplayName("requireClassDay: dos clases el lunes y se elige 20:40 → devuelve solo esa")
    void dayWithStartTimePicksOne() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(10, 25), 90),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(20, 40), 135)));

        ClassSlot slot = service.requireClassDay(1L, 9L, DayOfWeek.MONDAY, LocalTime.of(20, 40));

        assertThat(slot.recurringEventId()).isEqualTo(101L);
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(22, 55));
    }

    @Test
    @DisplayName("requireClassDay: una sola clase y se manda su horario → la devuelve")
    void dayWithStartTimeOnSingleClass() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L))
                .thenReturn(List.of(recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        assertThat(service.requireClassDay(1L, 9L, DayOfWeek.TUESDAY, LocalTime.of(18, 0)).recurringEventId())
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("requireClassDay: el horario enviado no coincide con ninguna clase → rechazado")
    void dayWithStartTimeNotMatching() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(10, 25), 90),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(20, 40), 135)));

        assertThatThrownBy(() -> service.requireClassDay(1L, 9L, DayOfWeek.MONDAY, LocalTime.of(12, 0)))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no tiene una clase")
                .hasMessageContaining("12:00");
    }

    @Test
    @DisplayName("requireClassDate: dos clases en la fecha y se elige 10:25 → devuelve esa")
    void dateWithStartTimePicksOne() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 100L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(10, 25), LocalTime.of(11, 55), null, null, null),
                new OccurrenceResponseDto(2L, 101L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(20, 40), LocalTime.of(22, 55), null, null, null)));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(10, 25), 90),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(20, 40), 135)));

        ClassSlot slot = service.requireClassDate(1L, 9L, monday, LocalTime.of(10, 25));

        assertThat(slot.recurringEventId()).isEqualTo(100L);
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(11, 55));
    }

    @Test
    @DisplayName("classDateSlots: agrupa por fecha, ordena horarios y junta tramos repetidos")
    void classDateSlotsGroupsByDate() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        LocalDate tuesday = LocalDate.of(2026, 9, 8);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 101L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(20, 40), LocalTime.of(22, 55), null, null, null),
                new OccurrenceResponseDto(2L, 100L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(10, 25), LocalTime.of(11, 55), null, null, null),
                new OccurrenceResponseDto(3L, 102L, tuesday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(18, 0), LocalTime.of(20, 0), null, null, null),
                new OccurrenceResponseDto(4L, 103L, tuesday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(18, 0), LocalTime.of(20, 0), null, null, null)));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(10, 25), 90),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(20, 40), 135),
                recurring(102L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120),
                recurring(103L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        var result = service.classDateSlots(1L, 9L, monday);

        assertThat(result).extracting(d -> d.date()).containsExactly(monday, tuesday);
        assertThat(result.get(0).slots()).extracting(t -> t.startTime())
                .containsExactly(LocalTime.of(10, 25), LocalTime.of(20, 40));
        assertThat(result.get(1).slots()).hasSize(1);
    }
}
