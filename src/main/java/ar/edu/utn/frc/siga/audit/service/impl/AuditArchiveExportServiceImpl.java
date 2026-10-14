package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.AuditArchivedCycleDto;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveRun;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveStatus;
import ar.edu.utn.frc.siga.audit.repository.ArchivedTable;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveExportRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRunRepository;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveExportService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditArchiveExportServiceImpl implements AuditArchiveExportService {

    private static final int PAGE_SIZE = 5000;

    private final AuditArchiveRunRepository runRepository;
    private final AuditArchiveExportRepository exportRepository;

    private record CycleRange(int cycle, LocalDate from, LocalDate to, LocalDateTime archivedAt) {
    }

    @Override
    public List<AuditArchivedCycleDto> findArchivedCycles() {
        List<AuditArchivedCycleDto> cycles = new ArrayList<>();
        for (CycleRange range : ranges()) {
            cycles.add(new AuditArchivedCycleDto(range.cycle(), range.from(), range.to(), range.archivedAt(),
                    exportRepository.count(ArchivedTable.OCCURRENCE, range.from(), range.to()),
                    exportRepository.count(ArchivedTable.ALLOCATION, range.from(), range.to())));
        }
        Collections.reverse(cycles);
        return cycles;
    }

    @Override
    public void exportCycle(int cycle, Supplier<OutputStream> output) {
        CycleRange range = ranges().stream()
                .filter(candidate -> candidate.cycle() == cycle)
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("ArchivedCycle", cycle));
        try (ZipOutputStream zip = new ZipOutputStream(output.get())) {
            for (ArchivedTable table : ArchivedTable.values()) {
                zip.putNextEntry(new ZipEntry(fileName(table)));
                writeCsv(table, range, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeCsv(ArchivedTable table, CycleRange range, OutputStream zip) throws IOException {
        ArchivedRowsCsvWriter csv = new ArchivedRowsCsvWriter(zip);
        long afterRev = -1;
        long afterId = -1;
        while (true) {
            List<Map<String, Object>> page = exportRepository.readPage(
                    table, range.from(), range.to(), afterRev, afterId, PAGE_SIZE);
            for (Map<String, Object> row : page) {
                csv.write(row);
            }
            csv.finish();
            if (page.size() < PAGE_SIZE) {
                return;
            }
            Map<String, Object> last = page.getLast();
            afterRev = ((Number) last.get("rev")).longValue();
            afterId = ((Number) last.get(table.idColumn())).longValue();
        }
    }

    private List<CycleRange> ranges() {
        Map<Integer, AuditArchiveRun> lastRunByCycle = new TreeMap<>();
        for (AuditArchiveRun run : runRepository.findByStatusOrderByCutoffAscExecutedAtAsc(AuditArchiveStatus.COMPLETED)) {
            lastRunByCycle.put(run.getRetainedFromCycle(), run);
        }
        List<CycleRange> ranges = new ArrayList<>();
        LocalDate previousCutoff = null;
        for (AuditArchiveRun run : lastRunByCycle.values()) {
            ranges.add(new CycleRange(run.getRetainedFromCycle() - 1, previousCutoff, run.getCutoff(), run.getExecutedAt()));
            previousCutoff = run.getCutoff();
        }
        return ranges;
    }

    private static String fileName(ArchivedTable table) {
        return table == ArchivedTable.OCCURRENCE ? "ocurrencias.csv" : "asignaciones.csv";
    }
}
