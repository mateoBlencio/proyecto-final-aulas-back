package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import ar.edu.utn.frc.siga.ingest.service.IngestService;
import ar.edu.utn.frc.siga.ingest.source.IngestSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestServiceImpl implements IngestService {

    private final List<IngestSource> sources;
    private final IngestRowBatchProcessor batchProcessor;
    private final AllocationService allocationService;

    @Override
    public IngestResultDto ingestFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(sin nombre)";
        log.info("Iniciando importación: {} - {} bytes", originalFilename, file.getSize());

        IngestSource source = sources.stream()
            .filter(s -> s.supports(originalFilename))
            .findFirst()
            .orElseThrow(() -> new InvalidFileFormatException("Formato de archivo no soportado: " + originalFilename));

        IngestSource.ParsedContent parsed = source.parse(file);
        IngestRowBatchProcessor.BatchResult result = batchProcessor.process(parsed.rows(), parsed.year());

        if (!result.pendingAllocations().isEmpty()) {
            allocationService.reallocate(AllocationCommand.imported(result.pendingAllocations(), "Importado de Excel"));
        }

        log.info("Importación completada: {} filas, {} períodos creados, {} eventos creados, {} filas salteadas, {} advertencias",
            result.processedRows(), result.periodsCreated(), result.eventsCreated(),
            result.skippedRows().size(), result.rowWarnings().size());

        return new IngestResultDto(result.processedRows(), result.periodsCreated(), result.eventsCreated(),
            result.skippedRows(), result.rowWarnings());
    }
}
