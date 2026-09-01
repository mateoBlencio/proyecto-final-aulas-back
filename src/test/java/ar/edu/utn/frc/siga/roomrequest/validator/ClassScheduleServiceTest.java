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

        assertThatThrownBy(() -> service.requireClassDay(1L, 9L, DayOfWeek.MONDAY))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no dicta clase");
    }

    @Test
    @DisplayName("requireClassDay: más de un bloque el mismo día → rechazado, no deriva un horario al azar")
    void ambiguousDay() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        assertThatThrownBy(() -> service.requireClassDay(1L, 9L, DayOfWeek.MONDAY))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("más de un bloque");
    }

    @Test
    @DisplayName("requireClassDay: un único bloque → devuelve ese slot")
    void singleBlock() {
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L))
                .thenReturn(List.of(recurring(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), 120)));

        ClassSlot slot = service.requireClassDay(1L, 9L, DayOfWeek.TUESDAY);

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

        ClassSlot slot = service.requireClassDay(1L, 9L, DayOfWeek.TUESDAY);

        assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("requireClassDate: resuelve el slot por el evento de la ocurrencia, no por el día")
    void dateResolvesByOccurrenceEvent() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of(
                new OccurrenceResponseDto(1L, 101L, monday, OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(14, 0), LocalTime.of(16, 0))));
        when(academicEventService.findRecurringEventsBySubjectAndCommission(1L, 9L)).thenReturn(List.of(
                recurring(100L, DayOfWeek.MONDAY, LocalTime.of(8, 0), 120),
                recurring(101L, DayOfWeek.MONDAY, LocalTime.of(14, 0), 120)));

        ClassSlot slot = service.requireClassDate(1L, 9L, monday);

        assertThat(slot.recurringEventId()).isEqualTo(101L);
        assertThat(slot.startTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("requireClassDate: fecha sin clase → rechazado")
    void dateNotTaught() {
        when(academicEventService.findClassOccurrences(eq(1L), eq(9L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireClassDate(1L, 9L, LocalDate.of(2026, 9, 7)))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no tiene clase");
    }
}
