package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadHolidayDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.time.LocalDate;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidaySyncService (thin syncer)")
class HolidaySyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private HolidayService holidayService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private HolidaySyncService service;

    @BeforeEach
    void setUp() {
        service = new HolidaySyncService(holidayService, syncStateService);
    }

    @Test
    @DisplayName("view: exposes the FERIADOS view")
    void viewReturnsFeriados() {
        assertThat(service.view()).isEqualTo(SysacadView.FERIADOS);
    }

    @Test
    @DisplayName("sync: translates each row to a command and records success with the affected rows")
    void syncTranslatesRowsAndRecordsSuccess() {
        when(catalogReader.findHolidays())
                .thenReturn(List.of(new SysacadHolidayDto(LocalDate.of(2026, 12, 8), "Inmaculada Concepción")));
        when(holidayService.syncHolidays(anyList())).thenReturn(4);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HolidaySyncCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(holidayService).syncHolidays(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new HolidaySyncCommand(LocalDate.of(2026, 12, 8), "Inmaculada Concepción"));
        verify(syncStateService).recordSuccess(SysacadView.FERIADOS, 4);
    }

    @Test
    @DisplayName("sync: when syncHolidays throws, records the failure and rethrows")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findHolidays())
                .thenReturn(List.of(new SysacadHolidayDto(LocalDate.of(2026, 12, 8), "X")));
        when(holidayService.syncHolidays(anyList())).thenThrow(new IllegalStateException("no valid rows"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.FERIADOS, "no valid rows");
    }
}
