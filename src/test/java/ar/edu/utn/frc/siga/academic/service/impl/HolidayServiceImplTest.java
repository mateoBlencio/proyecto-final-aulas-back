package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Holiday;
import ar.edu.utn.frc.siga.academic.repository.HolidayRepository;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayServiceImpl.syncHolidays")
class HolidayServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final LocalDate FUTURE = LocalDate.of(2026, 12, 8);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final LocalDate PAST = LocalDate.of(2026, 5, 25);
    private static final LocalDate OTHER_FUTURE = LocalDate.of(2026, 12, 25);

    @Mock
    private HolidayRepository holidayRepository;

    private HolidayServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HolidayServiceImpl(holidayRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Holiday holiday(LocalDate date, String description) {
        return Holiday.builder().date(date).description(description).syncedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    @Test
    @DisplayName("a future holiday missing from the run is deleted")
    void deletesFutureHolidayMissingFromRun() {
        Holiday future = holiday(FUTURE, "Inmaculada Concepción");
        when(holidayRepository.findAll()).thenReturn(List.of(future));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        verify(holidayRepository).delete(future);
    }

    @Test
    @DisplayName("today's holiday missing from the run is deleted")
    void deletesTodaysHolidayMissingFromRun() {
        Holiday today = holiday(TODAY, "Hoy");
        when(holidayRepository.findAll()).thenReturn(List.of(today));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        verify(holidayRepository).delete(today);
    }

    @Test
    @DisplayName("a past holiday missing from the run is kept")
    void keepsPastHolidayMissingFromRun() {
        when(holidayRepository.findAll()).thenReturn(List.of(holiday(PAST, "Revolución de Mayo")));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an existing past holiday with a different description is not modified")
    void doesNotModifyExistingPastHoliday() {
        Holiday past = holiday(PAST, "Old description");
        when(holidayRepository.findAll()).thenReturn(List.of(past));

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(PAST, "New description")));

        assertThat(past.getDescription()).isEqualTo("Old description");
        assertThat(affected).isZero();
        verify(holidayRepository, never()).save(any());
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a past holiday not stored yet is inserted with the clock's timestamp")
    void insertsMissingPastHoliday() {
        when(holidayRepository.findAll()).thenReturn(List.of());

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(PAST, "Revolución de Mayo")));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(PAST);
        assertThat(captor.getValue().getDescription()).isEqualTo("Revolución de Mayo");
        assertThat(captor.getValue().getSyncedAt()).isEqualTo(NOW);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("the same date twice with different descriptions keeps the first")
    void duplicateDateKeepsFirstDescription() {
        when(holidayRepository.findAll()).thenReturn(List.of());

        int affected = service.syncHolidays(List.of(
                new HolidaySyncCommand(FUTURE, "First"),
                new HolidaySyncCommand(FUTURE, "Second")));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("First");
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("a blank description is stored as null")
    void blankDescriptionStoredAsNull() {
        when(holidayRepository.findAll()).thenReturn(List.of());

        service.syncHolidays(List.of(new HolidaySyncCommand(FUTURE, "   ")));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("an empty list throws IllegalStateException without deleting or saving")
    void emptyListThrows() {
        assertThatThrownBy(() -> service.syncHolidays(List.of())).isInstanceOf(IllegalStateException.class);

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("a list with only null dates throws IllegalStateException without deleting or saving")
    void onlyNullDatesThrows() {
        assertThatThrownBy(() -> service.syncHolidays(List.of(
                new HolidaySyncCommand(null, "A"), new HolidaySyncCommand(null, "B"))))
                .isInstanceOf(IllegalStateException.class);

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("the return value sums inserts, updates and deletes")
    void returnsSumOfInsertsUpdatesAndDeletes() {
        LocalDate toUpdate = LocalDate.of(2026, 11, 20);
        LocalDate toInsert = LocalDate.of(2026, 11, 21);
        Holiday updated = holiday(toUpdate, "Old");
        Holiday deleted = holiday(FUTURE, "Gone");
        when(holidayRepository.findAll()).thenReturn(List.of(updated, deleted));

        int affected = service.syncHolidays(List.of(
                new HolidaySyncCommand(toUpdate, "New"),
                new HolidaySyncCommand(toInsert, "Inserted")));

        assertThat(affected).isEqualTo(3);
        assertThat(updated.getDescription()).isEqualTo("New");
        assertThat(updated.getSyncedAt()).isEqualTo(NOW);
        verify(holidayRepository).delete(deleted);
    }
}
