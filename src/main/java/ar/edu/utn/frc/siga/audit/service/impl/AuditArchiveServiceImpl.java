package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveRun;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveStatus;
import ar.edu.utn.frc.siga.audit.repository.ArchivedTable;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository.ArchiveBatch;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRunRepository;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import ar.edu.utn.frc.siga.audit.service.impl.ArchiveCutoffResolver.ArchiveCutoff;
import ar.edu.utn.frc.siga.common.util.Plurals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * No {@code @Transactional} on {@link #archive}: each step runs in its own transaction, so a failure keeps
 * the batches already moved and the next run resumes from what is left in {@code public}.
 */
@Slf4j
@Service
public class AuditArchiveServiceImpl implements AuditArchiveService {

    private static final String OPERATION = "Archivado de auditoría";

    private final AcademicPeriodService academicPeriodService;
    private final AuditArchiveRepository archiveRepository;
    private final AuditArchiveRunRepository runRepository;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public AuditArchiveServiceImpl(AcademicPeriodService academicPeriodService,
                                   AuditArchiveRepository archiveRepository,
                                   AuditArchiveRunRepository runRepository,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${siga.audit.archive.batch-size:5000}") int batchSize) {
        this.academicPeriodService = academicPeriodService;
        this.archiveRepository = archiveRepository;
        this.runRepository = runRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
    }

    @Override
    @AuditOperation(OPERATION)
    public AuditArchiveResultDto archive(LocalDate today) {
        ArchiveCutoffResolver.Resolution resolution = ArchiveCutoffResolver.resolve(academicPeriodService.findActive(), today);
        if (resolution.cutoff() == null) {
            return new AuditArchiveResultDto(resolution.failure(), null, null, 0, 0, 0);
        }
        ArchiveCutoff cutoff = resolution.cutoff();

        if (archiveRepository.countPending(cutoff.instant()) == 0) {
            log.info("Archivado de auditoría: nada anterior al {}", cutoff.startDate());
            return result(AuditArchiveOutcome.NOTHING_TO_ARCHIVE, cutoff, 0, 0, 0);
        }
        if (!archiveRepository.canCreateTempTables()) {
            throw new IllegalStateException("El rol de la aplicación no tiene el privilegio TEMP sobre la base: "
                    + "el archivado necesita tablas temporales");
        }
        // Probe the lock before writing the run, so a concurrent run leaves neither an empty run nor log entries.
        if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> archiveRepository.tryLock()))) {
            log.info("Archivado de auditoría omitido: otra ejecución tiene el lock");
            return result(AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN, cutoff, 0, 0, 0);
        }

        String prefix = OPERATION + " anterior al ciclo " + cutoff.retainedFromCycle() + ": ";
        AuditOperations.describe(prefix + "en curso");
        Long runId = transactionTemplate.execute(status -> runRepository.save(
                new AuditArchiveRun(LocalDateTime.now(), cutoff.retainedFromCycle(), cutoff.startDate())).getId());

        long[] totals = new long[3]; // occurrence rows, allocation rows, deleted revisions
        boolean stopped = false;
        try {
            for (ArchivedTable table : ArchivedTable.values()) {
                while (!stopped) {
                    Optional<ArchiveBatch> batch = transactionTemplate.execute(
                            status -> archiveRepository.archiveBatch(table, cutoff.instant(), batchSize));
                    if (batch.isEmpty()) {
                        stopped = true;
                        break;
                    }
                    if (batch.get().rows() == 0) {
                        break;
                    }
                    totals[table == ArchivedTable.OCCURRENCE ? 0 : 1] += batch.get().rows();
                    totals[2] += batch.get().deletedRevisions();
                }
            }
        } catch (RuntimeException e) {
            markFailed(runId, totals, e);
            AuditOperations.describe(prefix + "falló tras " + Plurals.count(totals[0] + totals[1], "fila", "filas"));
            throw e;
        }

        AuditArchiveStatus status = stopped ? AuditArchiveStatus.STOPPED_BY_CONCURRENT_RUN : AuditArchiveStatus.COMPLETED;
        AuditOperations.describe(prefix + Plurals.count(totals[0] + totals[1], "fila", "filas")
                + (stopped ? " (detenido: otra ejecución en curso)" : ""));
        updateRun(runId, totals, status, null);
        return result(stopped ? AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN : AuditArchiveOutcome.COMPLETED,
                cutoff, totals[0], totals[1], totals[2]);
    }

    /** A failure of this update must not hide the original exception. */
    private void markFailed(Long runId, long[] totals, RuntimeException cause) {
        log.error("Archivado de auditoría fallido", cause);
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        try {
            updateRun(runId, totals, AuditArchiveStatus.FAILED,
                    message.length() > 255 ? message.substring(0, 255) : message);
        } catch (RuntimeException e) {
            log.error("No se pudo marcar como fallido el archivado {}", runId, e);
        }
    }

    private void updateRun(Long runId, long[] totals, AuditArchiveStatus status, String errorMessage) {
        transactionTemplate.executeWithoutResult(tx -> {
            AuditArchiveRun run = runRepository.findById(runId).orElseThrow();
            run.setOccurrenceRows(totals[0]);
            run.setAllocationRows(totals[1]);
            run.setDeletedRevisions(totals[2]);
            run.setStatus(status);
            run.setErrorMessage(errorMessage);
        });
    }

    private static AuditArchiveResultDto result(AuditArchiveOutcome outcome, ArchiveCutoff cutoff,
                                                long occurrenceRows, long allocationRows, long deletedRevisions) {
        return new AuditArchiveResultDto(outcome, cutoff.retainedFromCycle(), cutoff.startDate(),
                occurrenceRows, allocationRows, deletedRevisions);
    }
}
