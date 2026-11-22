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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Procesa el lote en dos pasos para poder acotar la transacción: {@link #resolve} valida y resuelve
 * referencias (sin escribir eventos, fuera de transacción) y {@link #write} crea los eventos y arma las
 * asignaciones (lo invoca {@code IngestServiceImpl} dentro de su transacción, junto con la asignación).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IngestRowBatchProcessor {

    private final IngestRowResolver rowResolver;
    private final AcademicEventService academicEventService;

    record RowWork(int rowNum, RowDto dto, IngestRowResolver.ResolvedRefs refs) {
    }

    record ResolvedBatch(List<RowWork> work, int periodsCreated, List<RowIssueDto> skippedRows) {
    }

    record BatchResult(int processedRows, int periodsCreated, int eventsCreated,
            List<RowIssueDto> skippedRows, List<RowIssueDto> rowWarnings, List<AllocationItem> pendingAllocations) {
    }

    ResolvedBatch resolve(List<ImportedRow> rows, int year) {
        // Primero se valida todo el archivo: un error de fila aborta con 422 sin haber resuelto ni escrito nada.
        List<TermType> termTypes = rows.stream().map(IngestRowBatchProcessor::parseTermType).toList();

        AtomicInteger periodsCreated = new AtomicInteger(0);
        List<RowIssueDto> skippedRows = new ArrayList<>();
        IngestCache cache = new IngestCache();

        List<RowWork> work = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportedRow importedRow = rows.get(i);
            int rowNum = importedRow.rowNumber();
            RowDto dto = importedRow.data();
            TermType termType = termTypes.get(i);

            try {
                IngestRowResolver.ResolvedRefs refs = rowResolver.resolveRefs(
                    dto, termType, year, termType.startDate(year), termType.endDate(year), cache, periodsCreated);
                work.add(new RowWork(rowNum, dto, refs));
            } catch (ResourceNotFoundException e) {
                skippedRows.add(new RowIssueDto(rowNum, e.getMessage()));
                log.warn("Fila {} salteada, no resuelve contra el catálogo: {}", rowNum, e.getMessage());
            }
        }
        return new ResolvedBatch(work, periodsCreated.get(), skippedRows);
    }

    /**
     * Las advertencias no lanzan excepciones: no abortan la transacción del llamador y viajan en el resultado.
     */
    BatchResult write(ResolvedBatch batch) {
        List<RowWork> work = batch.work();
        List<FindOrCreateResult<Long>> events = work.isEmpty()
            ? List.of()
            : academicEventService.findOrCreateRecurringEvents(
                work.stream().map(w -> w.refs().eventRequest()).toList());

        List<RowIssueDto> rowWarnings = new ArrayList<>();
        Map<Long, List<RowWork>> rowsByEvent = new LinkedHashMap<>();
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
            rowsByEvent.computeIfAbsent(event.value(), id -> new ArrayList<>()).add(w);
            log.debug("Fila {}: subject={}, commission={}, classroom={}",
                w.rowNum(), refs.subject().name(), refs.commission().courseCode(), w.dto().roomNumber());
        }

        List<AllocationItem> pendingAllocations = new ArrayList<>();
        rowsByEvent.forEach((eventId, eventRows) -> {
            Set<Long> classroomIds = eventRows.stream()
                .map(w -> w.refs().classroom().id())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (classroomIds.size() == 1) {
                pendingAllocations.add(new AllocationItem(
                    new AllocationTarget.Event(eventId), classroomIds.iterator().next()));
            } else {
                rowWarnings.addAll(conflictingClassroomWarnings(eventId, eventRows));
            }
        });
        rowWarnings.sort(Comparator.comparingInt(RowIssueDto::row));

        return new BatchResult(work.size(), batch.periodsCreated(), eventsCreated,
            batch.skippedRows(), rowWarnings, pendingAllocations);
    }

    private static List<RowIssueDto> conflictingClassroomWarnings(Long eventId, List<RowWork> eventRows) {
        List<RowIssueDto> warnings = new ArrayList<>();
        for (RowWork w : eventRows) {
            Long classroomId = w.refs().classroom().id();
            List<Integer> otherRows = eventRows.stream()
                .filter(o -> !o.refs().classroom().id().equals(classroomId))
                .map(RowWork::rowNum)
                .toList();
            warnings.add(new RowIssueDto(w.rowNum(), "La fila " + w.rowNum() + " apunta al mismo evento que las filas "
                + otherRows + " con otra aula; no se asignó ninguna"));
            log.warn("Fila {}: el evento {} aparece con aulas distintas en las filas {}; no se asigna",
                w.rowNum(), eventId, otherRows);
        }
        return warnings;
    }

    private static TermType parseTermType(ImportedRow row) {
        return TermType.fromLabel(row.data().termType())
            .orElseThrow(() -> new InvalidRowException(
                "Unknown term type: '" + row.data().termType() + "', row " + row.rowNumber()));
    }
}
