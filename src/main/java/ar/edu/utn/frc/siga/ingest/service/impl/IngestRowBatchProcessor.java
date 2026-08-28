package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.ingest.dto.RowDto;
import ar.edu.utn.frc.siga.ingest.dto.ImportedRow;
import ar.edu.utn.frc.siga.ingest.dto.RowIssueDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidRowException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class IngestRowBatchProcessor {

    private final IngestRowResolver rowResolver;

    record BatchResult(int processedRows, int periodsCreated, int eventsCreated,
            List<RowIssueDto> skippedRows, List<RowIssueDto> rowWarnings, List<AllocationItem> pendingAllocations) {
    }

    BatchResult process(List<ImportedRow> rows, int year) {
        int processedRows = 0;
        AtomicInteger periodsCreated = new AtomicInteger(0);
        AtomicInteger eventsCreated = new AtomicInteger(0);
        List<RowIssueDto> skippedRows = new ArrayList<>();
        List<RowIssueDto> rowWarnings = new ArrayList<>();
        List<AllocationItem> pendingAllocations = new ArrayList<>();
        IngestCache cache = new IngestCache();

        for (ImportedRow importedRow : rows) {
            int rowNum = importedRow.rowNumber();
            RowDto dto = importedRow.data();

            TermType termType = TermType.fromLabel(dto.termType())
                .orElseThrow(() -> new InvalidRowException(
                    "Unknown term type: '" + dto.termType() + "', row " + rowNum));
            LocalDate startDate = termType.startDate(year);
            LocalDate endDate = termType.endDate(year);

            try {
                IngestRowResolver.ResolvedRow resolved =
                    rowResolver.resolve(dto, termType, year, startDate, endDate, cache, periodsCreated);
                if (resolved.eventCreated()) eventsCreated.incrementAndGet();

                if (!resolved.classroom().buildingId().equals(resolved.building().id())) {
                    rowWarnings.add(new RowIssueDto(rowNum, "Aula '" + dto.roomNumber() + "' no pertenece al edificio "
                        + "informado ('" + dto.buildingName() + "'); se usó su edificio real ('" + resolved.classroom().buildingName() + "')"));
                }

                pendingAllocations.add(new AllocationItem(
                    new AllocationTarget.Event(resolved.eventId()),
                    resolved.classroom().id()
                ));

                processedRows++;
                log.debug("Fila {}: subject={}, commission={}, classroom={}",
                    rowNum, resolved.subject().name(), resolved.commission().courseCode(), dto.roomNumber());
            } catch (ResourceNotFoundException e) {
                skippedRows.add(new RowIssueDto(rowNum, e.getMessage()));
                log.warn("Fila {} salteada, no resuelve contra el catálogo: {}", rowNum, e.getMessage());
            }
        }

        return new BatchResult(processedRows, periodsCreated.get(), eventsCreated.get(),
            skippedRows, rowWarnings, pendingAllocations);
    }
}
