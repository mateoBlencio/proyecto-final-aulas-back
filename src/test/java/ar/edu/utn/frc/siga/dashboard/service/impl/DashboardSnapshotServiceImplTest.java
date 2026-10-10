package ar.edu.utn.frc.siga.dashboard.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardReadRepository;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRegenerationRepository;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@DisplayName("DashboardSnapshotServiceImpl.generateMissingMonths")
class DashboardSnapshotServiceImplTest {

    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final YearMonth JANUARY = YearMonth.of(2027, 1);
    private static final YearMonth FEBRUARY = YearMonth.of(2027, 2);
    private static final YearMonth MARCH = YearMonth.of(2027, 3);

    private DashboardSnapshotRepository snapshotRepository;
    private DashboardSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(DashboardSnapshotRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2027-03-01T06:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        // generate() is stubbed on the spy: these tests only check which months generateMissingMonths asks for.
        service = spy(new DashboardSnapshotServiceImpl(null, mock(DashboardReadRepository.class), snapshotRepository,
                mock(DashboardSnapshotRegenerationRepository.class), null, new ObjectMapper(),
                mock(PlatformTransactionManager.class), clock));
        doNothing().when(service).generate(any(), anyString(), isNull());
    }

    private static DashboardSnapshot campusRow(YearMonth month) {
        return DashboardSnapshot.builder().year(month.getYear()).month(month.getMonthValue()).build();
    }

    private static DashboardSnapshot buildingRow(YearMonth month, long buildingId) {
        return DashboardSnapshot.builder().year(month.getYear()).month(month.getMonthValue())
                .buildingId(buildingId).build();
    }

    @Test
    @DisplayName("without snapshots it generates only the last closed month")
    void withoutSnapshotsGeneratesOnlyLastClosedMonth() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of());

        service.generateMissingMonths();

        verify(service).generate(FEBRUARY, "SISTEMA", null);
        verify(service, never()).generate(eq(JANUARY), anyString(), any());
        verify(service, never()).generate(eq(MARCH), anyString(), any());
    }

    @Test
    @DisplayName("with current snapshots for 2026-12 and 2027-02 it generates only 2027-01")
    void generatesOnlyTheGapBetweenSnapshots() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of(
                campusRow(DECEMBER), buildingRow(DECEMBER, 1L), campusRow(FEBRUARY), buildingRow(FEBRUARY, 1L)));

        service.generateMissingMonths();

        verify(service).generate(JANUARY, "SISTEMA", null);
        verify(service, never()).generate(eq(DECEMBER), anyString(), any());
        verify(service, never()).generate(eq(FEBRUARY), anyString(), any());
        verify(service, never()).generate(eq(MARCH), anyString(), any());
    }

    @Test
    @DisplayName("a month with building rows but no campus row counts as missing")
    void monthWithoutCampusRowIsMissing() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of(
                campusRow(DECEMBER), buildingRow(JANUARY, 1L), campusRow(FEBRUARY)));

        service.generateMissingMonths();

        verify(service).generate(JANUARY, "SISTEMA", null);
        verify(service, never()).generate(eq(FEBRUARY), anyString(), any());
    }

    @Test
    @DisplayName("when 2027-01 fails it still tries 2027-02")
    void failureInOneMonthDoesNotStopTheNext() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of(campusRow(DECEMBER)));
        doThrow(new IllegalStateException("boom")).when(service).generate(eq(JANUARY), anyString(), isNull());

        service.generateMissingMonths();

        verify(service).generate(JANUARY, "SISTEMA", null);
        verify(service).generate(FEBRUARY, "SISTEMA", null);
    }

    @Test
    @DisplayName("it never generates the month in progress")
    void neverGeneratesCurrentMonth() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of(campusRow(DECEMBER)));

        service.generateMissingMonths();

        verify(service, never()).generate(eq(MARCH), anyString(), any());
    }

    @Test
    @DisplayName("with every month up to 2027-02 already stored it generates nothing")
    void nothingMissingGeneratesNothing() {
        when(snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()).thenReturn(List.of(
                campusRow(DECEMBER), campusRow(JANUARY), campusRow(FEBRUARY)));

        service.generateMissingMonths();

        verify(service, never()).generate(any(), anyString(), any());
    }
}
