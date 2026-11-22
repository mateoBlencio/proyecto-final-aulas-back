package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
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
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayServiceImpl")
class HolidayServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant EARLIER_DELETION = Instant.parse("2026-09-01T00:00:00Z");
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

    private static Holiday softDeletedHoliday(LocalDate date, String description) {
        Holiday holiday = holiday(date, description);
        holiday.setDeletedAt(EARLIER_DELETION);
        return holiday;
    }

    @Test
    @DisplayName("syncHolidays: a future holiday missing from the run is soft-deleted with the run's timestamp, never deleted")
    void softDeletesFutureHolidayMissingFromRun() {
        Holiday future = holiday(FUTURE, "Inmaculada Concepción");
        when(holidayRepository.findAll()).thenReturn(List.of(future));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        assertThat(future.getDeletedAt()).isEqualTo(NOW);
        verify(holidayRepository).save(future);
        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).deleteById(any());
        verify(holidayRepository, never()).deleteAll(anyIterable());
    }

    @Test
    @DisplayName("syncHolidays: today's holiday missing from the run is soft-deleted")
    void softDeletesTodaysHolidayMissingFromRun() {
        Holiday today = holiday(TODAY, "Hoy");
        when(holidayRepository.findAll()).thenReturn(List.of(today));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        assertThat(today.getDeletedAt()).isEqualTo(NOW);
        verify(holidayRepository).save(today);
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("syncHolidays: a past holiday missing from the run is kept without soft-deletion")
    void keepsPastHolidayMissingFromRun() {
        Holiday past = holiday(PAST, "Revolución de Mayo");
        when(holidayRepository.findAll()).thenReturn(List.of(past));

        service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        assertThat(past.getDeletedAt()).isNull();
        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(past);
    }

    @Test
    @DisplayName("syncHolidays: a soft-deleted holiday that comes back is reactivated with the new description and counts as affected")
    void reactivatesSoftDeletedHolidayThatComesBack() {
        Holiday deleted = softDeletedHoliday(FUTURE, "Old description");
        when(holidayRepository.findAll()).thenReturn(List.of(deleted));

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(FUTURE, "New description")));

        assertThat(deleted.getDeletedAt()).isNull();
        assertThat(deleted.getDescription()).isEqualTo("New description");
        assertThat(deleted.getSyncedAt()).isEqualTo(NOW);
        assertThat(affected).isEqualTo(1);
        verify(holidayRepository).save(deleted);
    }

    @Test
    @DisplayName("syncHolidays: a soft-deleted holiday that is still missing is neither saved again nor counted")
    void doesNotRecountAlreadySoftDeletedHoliday() {
        Holiday deleted = softDeletedHoliday(FUTURE, "Gone");
        Holiday stillListed = holiday(OTHER_FUTURE, "Navidad");
        when(holidayRepository.findAll()).thenReturn(List.of(deleted, stillListed));

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(OTHER_FUTURE, "Navidad")));

        assertThat(affected).isZero();
        assertThat(deleted.getDeletedAt()).isEqualTo(EARLIER_DELETION);
        verify(holidayRepository, never()).save(any());
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("syncHolidays: an active future holiday present in the run stays active")
    void keepsActiveHolidayPresentInRun() {
        Holiday future = holiday(FUTURE, "Inmaculada Concepción");
        when(holidayRepository.findAll()).thenReturn(List.of(future));

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(FUTURE, "Inmaculada Concepción")));

        assertThat(future.getDeletedAt()).isNull();
        assertThat(affected).isZero();
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncHolidays: an existing past holiday with a different description is not modified")
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
    @DisplayName("syncHolidays: a past holiday not stored yet is inserted with the clock's timestamp")
    void insertsMissingPastHoliday() {
        when(holidayRepository.findAll()).thenReturn(List.of());

        int affected = service.syncHolidays(List.of(new HolidaySyncCommand(PAST, "Revolución de Mayo")));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(PAST);
        assertThat(captor.getValue().getDescription()).isEqualTo("Revolución de Mayo");
        assertThat(captor.getValue().getSyncedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getDeletedAt()).isNull();
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncHolidays: the same date twice with different descriptions keeps the first")
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
    @DisplayName("syncHolidays: a blank description is stored as null")
    void blankDescriptionStoredAsNull() {
        when(holidayRepository.findAll()).thenReturn(List.of());

        service.syncHolidays(List.of(new HolidaySyncCommand(FUTURE, "   ")));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("syncHolidays: an empty list throws IllegalStateException without soft-deleting or saving")
    void emptyListThrows() {
        assertThatThrownBy(() -> service.syncHolidays(List.of())).isInstanceOf(IllegalStateException.class);

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncHolidays: a list with only null dates throws IllegalStateException without soft-deleting or saving")
    void onlyNullDatesThrows() {
        assertThatThrownBy(() -> service.syncHolidays(List.of(
                new HolidaySyncCommand(null, "A"), new HolidaySyncCommand(null, "B"))))
                .isInstanceOf(IllegalStateException.class);

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncHolidays: the return value sums inserts, updates, reactivations and soft-deletions")
    void returnsSumOfInsertsUpdatesReactivationsAndSoftDeletions() {
        LocalDate toUpdate = LocalDate.of(2026, 11, 20);
        LocalDate toInsert = LocalDate.of(2026, 11, 21);
        LocalDate toReactivate = LocalDate.of(2026, 11, 22);
        Holiday updated = holiday(toUpdate, "Old");
        Holiday deleted = holiday(FUTURE, "Gone");
        Holiday reactivated = softDeletedHoliday(toReactivate, "Back");
        when(holidayRepository.findAll()).thenReturn(List.of(updated, deleted, reactivated));

        int affected = service.syncHolidays(List.of(
                new HolidaySyncCommand(toUpdate, "New"),
                new HolidaySyncCommand(toInsert, "Inserted"),
                new HolidaySyncCommand(toReactivate, "Back")));

        assertThat(affected).isEqualTo(4);
        assertThat(updated.getDescription()).isEqualTo("New");
        assertThat(updated.getSyncedAt()).isEqualTo(NOW);
        assertThat(deleted.getDeletedAt()).isEqualTo(NOW);
        assertThat(reactivated.getDeletedAt()).isNull();
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("findBetween: queries through the repository method that excludes soft-deleted holidays and maps date and description")
    void findBetweenUsesRepositoryQueryThatExcludesSoftDeleted() {
        LocalDate from = LocalDate.of(2026, 12, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(holidayRepository.findByDateBetweenAndDeletedAtIsNullOrderByDateAsc(from, to))
                .thenReturn(List.of(holiday(FUTURE, "Inmaculada Concepción")));

        assertThat(service.findBetween(from, to))
                .containsExactly(new HolidayResponseDto(FUTURE, "Inmaculada Concepción"));
    }
}
