package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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
    private final AcademicEventService academicEventService;

    record BatchResult(int processedRows, int periodsCreated, int eventsCreated,
            List<RowIssueDto> skippedRows, List<RowIssueDto> rowWarnings, List<AllocationItem> pendingAllocations) {
    }

    private record RowWork(int rowNum, RowDto dto, IngestRowResolver.ResolvedRefs refs) {
    }

    BatchResult process(List<ImportedRow> rows, int year) {
        AtomicInteger periodsCreated = new AtomicInteger(0);
        List<RowIssueDto> skippedRows = new ArrayList<>();
        List<RowIssueDto> rowWarnings = new ArrayList<>();
        List<AllocationItem> pendingAllocations = new ArrayList<>();
        IngestCache cache = new IngestCache();

        List<RowWork> work = new ArrayList<>();
        for (ImportedRow importedRow : rows) {
            int rowNum = importedRow.rowNumber();
            RowDto dto = importedRow.data();

            TermType termType = TermType.fromLabel(dto.termType())
                .orElseThrow(() -> new InvalidRowException(
                    "Unknown term type: '" + dto.termType() + "', row " + rowNum));
            LocalDate startDate = termType.startDate(year);
            LocalDate endDate = termType.endDate(year);

            try {
                IngestRowResolver.ResolvedRefs refs =
                    rowResolver.resolveRefs(dto, termType, year, startDate, endDate, cache, periodsCreated);
                work.add(new RowWork(rowNum, dto, refs));
            } catch (ResourceNotFoundException e) {
                skippedRows.add(new RowIssueDto(rowNum, e.getMessage()));
                log.warn("Fila {} salteada, no resuelve contra el catálogo: {}", rowNum, e.getMessage());
            }
        }

        List<FindOrCreateResult<Long>> events = work.isEmpty()
            ? List.of()
            : academicEventService.findOrCreateRecurringEvents(
                work.stream().map(w -> w.refs().eventRequest()).toList());

        int processedRows = 0;
        int eventsCreated = 0;
        for (int i = 0; i < work.size(); i++) {
            RowWork w = work.get(i);
            FindOrCreateResult<Long> event = events.get(i);
            IngestRowResolver.ResolvedRefs refs = w.refs();

            if (event.created()) {
                eventsCreated++;
            }
            if (!refs.classroom().buildingId().equals(refs.building().id())) {
                rowWarnings.add(new RowIssueDto(w.rowNum(), "Aula '" + w.dto().roomNumber() + "' no pertenece al edificio "
                    + "informado ('" + w.dto().buildingName() + "'); se usó su edificio real ('" + refs.classroom().buildingName() + "')"));
            }
            pendingAllocations.add(new AllocationItem(
                new AllocationTarget.Event(event.value()),
                refs.classroom().id()
            ));
            processedRows++;
            log.debug("Fila {}: subject={}, commission={}, classroom={}",
                w.rowNum(), refs.subject().name(), refs.commission().courseCode(), w.dto().roomNumber());
        }

        return new BatchResult(processedRows, periodsCreated.get(), eventsCreated,
            skippedRows, rowWarnings, pendingAllocations);
    }
}
