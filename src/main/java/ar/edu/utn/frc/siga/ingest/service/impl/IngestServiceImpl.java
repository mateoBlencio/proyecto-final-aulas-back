package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.OccurrenceBoardService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import ar.edu.utn.frc.siga.ingest.service.IngestService;
import ar.edu.utn.frc.siga.ingest.source.IngestSource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class IngestServiceImpl implements IngestService {

    private final List<IngestSource> sources;
    private final IngestRowBatchProcessor batchProcessor;
    private final AllocationService allocationService;
    private final OccurrenceBoardService occurrenceBoardService;
    private final TransactionTemplate transactionTemplate;

    public IngestServiceImpl(List<IngestSource> sources, IngestRowBatchProcessor batchProcessor,
            AllocationService allocationService, OccurrenceBoardService occurrenceBoardService,
            PlatformTransactionManager transactionManager) {
        this.sources = sources;
        this.batchProcessor = batchProcessor;
        this.allocationService = allocationService;
        this.occurrenceBoardService = occurrenceBoardService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * No es {@code @Transactional}: las búsquedas de catálogo son de solo lectura y una fila que no resuelve
     * lanza {@code ResourceNotFoundException}, que marcaría rollback-only una transacción externa y haría
     * fallar toda la importación. La transacción cubre solo la creación de eventos y las asignaciones, todo
     * o nada: si una asignación falla (por ejemplo por solape), no queda ningún evento creado. Los períodos
     * creados al resolver quedan aunque la escritura falle (son datos de referencia idempotentes).
     */
    @Override
    @AuditOperation(value = "Importación de eventos desde archivo", subject = "AcademicEvent")
    public IngestResultDto ingestFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(sin nombre)";
        AuditOperations.describe("Importación de eventos desde archivo " + originalFilename.replaceAll("\\p{Cntrl}", " "));
        log.info("Iniciando importación: {} - {} bytes", originalFilename, file.getSize());

        IngestSource source = sources.stream()
            .filter(s -> s.supports(originalFilename))
            .findFirst()
            .orElseThrow(() -> new InvalidFileFormatException("Formato de archivo no soportado: " + originalFilename));

        IngestSource.ParsedContent parsed = source.parse(file);
        IngestRowBatchProcessor.ResolvedBatch resolved = batchProcessor.resolve(parsed.rows(), parsed.year());

        IngestRowBatchProcessor.BatchResult result = transactionTemplate.execute(status -> {
            IngestRowBatchProcessor.BatchResult written = batchProcessor.write(resolved);
            if (!written.pendingAllocations().isEmpty()) {
                SystemScope.run(() -> allocationService.reallocate(
                    AllocationCommand.imported(written.pendingAllocations(), "Importado de Excel")));
            }
            return written;
        });
        occurrenceBoardService.invalidateCache();

        log.info("Importación completada: {} filas, {} períodos creados, {} eventos creados, {} filas salteadas, {} advertencias",
            result.processedRows(), result.periodsCreated(), result.eventsCreated(),
            result.skippedRows().size(), result.rowWarnings().size());

        return new IngestResultDto(result.processedRows(), result.periodsCreated(), result.eventsCreated(),
            result.skippedRows(), result.rowWarnings());
    }
}
