package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveRun;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveStatus;
import ar.edu.utn.frc.siga.audit.repository.ArchivedTable;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository.ArchiveBatch;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditArchiveServiceImpl")
class AuditArchiveServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate CUTOFF = LocalDate.of(2025, 3, 3);
    private static final LocalDateTime CUTOFF_INSTANT = CUTOFF.atStartOfDay();
    private static final int BATCH_SIZE = 5;
    private static final long RUN_ID = 42L;

    @Mock
    private AcademicPeriodService academicPeriodService;
    @Mock
    private AuditArchiveRepository archiveRepository;
    @Mock
    private AuditArchiveRunRepository runRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuditArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditArchiveServiceImpl(academicPeriodService, archiveRepository, runRepository,
                transactionManager, BATCH_SIZE);
    }

    private void withPeriods() {
        when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(2025, 0, CUTOFF, LocalDate.of(2025, 12, 1)),
                new AcademicPeriodResponseDto(2026, 0, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 1))));
    }

    private void withTransactions() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    /** Pending rows, TEMP privilege and a free lock: the path that reaches the batches. */
    private void readyToArchive(long pending) {
        withPeriods();
        withTransactions();
        withSavedRun();
        when(archiveRepository.countPending(CUTOFF_INSTANT)).thenReturn(pending);
        when(archiveRepository.canCreateTempTables()).thenReturn(true);
        when(archiveRepository.tryLock()).thenReturn(true);
    }

    /** The saved run gets an id and is returned by {@code findById}, as the database would do. */
    private void withSavedRun() {
        AuditArchiveRun[] saved = new AuditArchiveRun[1];
        when(runRepository.save(any(AuditArchiveRun.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved[0], "id", RUN_ID);
            return saved[0];
        });
        lenient().when(runRepository.findById(RUN_ID)).thenAnswer(invocation -> Optional.of(saved[0]));
    }

    private static Optional<ArchiveBatch> batch(int rows, int deletedRevisions) {
        return Optional.of(new ArchiveBatch(rows, deletedRevisions));
    }

    @Test
    @DisplayName("sin períodos para el corte devuelve NO_PERIODS y no toca ni la base ni la transacción")
    void noPeriodsWritesNothing() {
        when(academicPeriodService.findActive()).thenReturn(List.of());

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.NO_PERIODS, null, null, 0, 0, 0));
        verifyNoInteractions(archiveRepository, runRepository, transactionManager);
    }

    @Test
    @DisplayName("sin filas pendientes devuelve NOTHING_TO_ARCHIVE con el corte y no crea el registro ni lotes")
    void nothingPendingWritesNothing() {
        withPeriods();
        when(archiveRepository.countPending(CUTOFF_INSTANT)).thenReturn(0L);

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(
                new AuditArchiveResultDto(AuditArchiveOutcome.NOTHING_TO_ARCHIVE, 2025, CUTOFF, 0, 0, 0));
        verifyNoInteractions(runRepository, transactionManager);
        verify(archiveRepository, never()).archiveBatch(any(), any(), anyInt());
    }

    @Test
    @DisplayName("COMPLETED: suma los lotes de ocurrencias y de asignaciones y cierra el registro con los totales")
    void completedSumsBatchesAndClosesTheRun() {
        readyToArchive(13L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(5, 1), batch(5, 2), batch(0, 0));
        when(archiveRepository.archiveBatch(ArchivedTable.ALLOCATION, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(3, 1), batch(0, 0));

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.COMPLETED, 2025, CUTOFF, 10, 3, 4));
        ArgumentCaptor<AuditArchiveRun> run = ArgumentCaptor.forClass(AuditArchiveRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(AuditArchiveStatus.COMPLETED);
        assertThat(run.getValue().getRetainedFromCycle()).isEqualTo(2025);
        assertThat(run.getValue().getCutoff()).isEqualTo(CUTOFF);
        assertThat(run.getValue().getOccurrenceRows()).isEqualTo(10);
        assertThat(run.getValue().getAllocationRows()).isEqualTo(3);
        assertThat(run.getValue().getDeletedRevisions()).isEqualTo(4);
    }

    @Test
    @DisplayName("el orden es prueba del lock, alta del registro, lotes de OCCURRENCE hasta cero, lotes de ALLOCATION hasta cero y cierre")
    void ordersRunBatchesThenClose() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(5, 0), batch(0, 0));
        when(archiveRepository.archiveBatch(ArchivedTable.ALLOCATION, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(3, 0), batch(0, 0));

        service.archive(TODAY);

        InOrder order = inOrder(archiveRepository, runRepository);
        order.verify(archiveRepository).countPending(CUTOFF_INSTANT);
        order.verify(archiveRepository).tryLock();
        order.verify(runRepository).save(any(AuditArchiveRun.class));
        order.verify(archiveRepository, times(2)).archiveBatch(eq(ArchivedTable.OCCURRENCE), any(), anyInt());
        order.verify(archiveRepository, times(2)).archiveBatch(eq(ArchivedTable.ALLOCATION), any(), anyInt());
        order.verify(runRepository).findById(RUN_ID);
    }

    @Test
    @DisplayName("el lock ocupado en el primer lote da STOPPED_BY_CONCURRENT_RUN, sin tocar ALLOCATION y con el registro en ese estado")
    void lockBusyOnFirstBatchStopsEverything() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(Optional.empty());

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(
                new AuditArchiveResultDto(AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN, 2025, CUTOFF, 0, 0, 0));
        verify(archiveRepository, never()).archiveBatch(eq(ArchivedTable.ALLOCATION), any(), anyInt());
        ArgumentCaptor<AuditArchiveRun> run = ArgumentCaptor.forClass(AuditArchiveRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(AuditArchiveStatus.STOPPED_BY_CONCURRENT_RUN);
    }

    @Test
    @DisplayName("si el lock se pierde en las asignaciones conserva las filas ya movidas de ocurrencias")
    void lockLostOnSecondTableKeepsTheFirstCounts() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(5, 1), batch(0, 0));
        when(archiveRepository.archiveBatch(ArchivedTable.ALLOCATION, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(Optional.empty());

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(
                new AuditArchiveResultDto(AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN, 2025, CUTOFF, 5, 0, 1));
    }

    @Test
    @DisplayName("cada lote corre en su propia transacción: prueba del lock, alta, 2 lotes de ocurrencias, 2 de asignaciones y cierre hacen 7 commits")
    void everyStepCommitsOnItsOwn() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(5, 0), batch(0, 0));
        when(archiveRepository.archiveBatch(ArchivedTable.ALLOCATION, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(3, 0), batch(0, 0));

        service.archive(TODAY);

        verify(transactionManager, times(7)).commit(any());
    }

    @Test
    @DisplayName("si el segundo lote falla relanza el error y deja el registro FAILED con los contadores del primero y el mensaje")
    void batchFailureMarksTheRunFailed() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenReturn(batch(5, 1))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.archive(TODAY)).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        verify(transactionManager).rollback(any());
        verify(archiveRepository, never()).archiveBatch(eq(ArchivedTable.ALLOCATION), any(), anyInt());
        ArgumentCaptor<AuditArchiveRun> run = ArgumentCaptor.forClass(AuditArchiveRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(AuditArchiveStatus.FAILED);
        assertThat(run.getValue().getOccurrenceRows()).isEqualTo(5);
        assertThat(run.getValue().getDeletedRevisions()).isEqualTo(1);
        assertThat(run.getValue().getErrorMessage()).isEqualTo("IllegalStateException: boom");
    }

    @Test
    @DisplayName("el mensaje de error del registro se trunca a 255 caracteres")
    void errorMessageIsTruncated() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenThrow(new IllegalStateException("x".repeat(400)));

        assertThatThrownBy(() -> service.archive(TODAY)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<AuditArchiveRun> run = ArgumentCaptor.forClass(AuditArchiveRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getErrorMessage()).hasSize(255);
    }

    @Test
    @DisplayName("si marcar el registro como FAILED también falla, se propaga el error original del lote")
    void failureWhileMarkingFailedDoesNotHideTheOriginal() {
        readyToArchive(8L);
        when(archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, CUTOFF_INSTANT, BATCH_SIZE))
                .thenThrow(new IllegalStateException("original"));
        doThrow(new IllegalArgumentException("db down")).when(runRepository).findById(RUN_ID);

        assertThatThrownBy(() -> service.archive(TODAY)).isInstanceOf(IllegalStateException.class).hasMessage("original");
    }

    @Test
    @DisplayName("con el lock ocupado antes de empezar devuelve STOPPED_BY_CONCURRENT_RUN sin grabar el registro ni abrir lotes")
    void busyLockBeforeStartWritesNoRun() {
        withPeriods();
        withTransactions();
        when(archiveRepository.countPending(CUTOFF_INSTANT)).thenReturn(8L);
        when(archiveRepository.canCreateTempTables()).thenReturn(true);
        when(archiveRepository.tryLock()).thenReturn(false);

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(
                new AuditArchiveResultDto(AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN, 2025, CUTOFF, 0, 0, 0));
        verifyNoInteractions(runRepository);
        verify(archiveRepository, never()).archiveBatch(any(), any(), anyInt());
    }

    @Test
    @DisplayName("sin privilegio TEMP lanza IllegalStateException con un mensaje claro y no toma el lock ni graba el registro")
    void missingTempPrivilegeFailsBeforeAnyWrite() {
        withPeriods();
        when(archiveRepository.countPending(CUTOFF_INSTANT)).thenReturn(8L);
        when(archiveRepository.canCreateTempTables()).thenReturn(false);

        assertThatThrownBy(() -> service.archive(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEMP");

        verify(archiveRepository, never()).tryLock();
        verifyNoInteractions(runRepository, transactionManager);
    }

    @Test
    @DisplayName("con períodos inválidos devuelve INVALID_PERIODS y no toca la base")
    void invalidPeriodsWritesNothing() {
        // 2025 starts the same day as 2026: the cutoff would not be before the current cycle.
        when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(2025, 0, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 1)),
                new AcademicPeriodResponseDto(2026, 0, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 1))));

        AuditArchiveResultDto result = service.archive(TODAY);

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.INVALID_PERIODS, null, null, 0, 0, 0));
        verifyNoInteractions(archiveRepository, runRepository, transactionManager);
    }
}
