package ar.edu.utn.frc.siga.excelimport.service.impl;

import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.siga.excelimport.dto.RowIssueDto;
import ar.edu.utn.frc.siga.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.siga.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.siga.excelimport.service.ExcelImportService;
import ar.edu.utn.frc.siga.excelimport.validator.ExcelTemplateValidator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementación de la importación masiva desde Excel: valida la plantilla y recorre las
 * filas de datos (fila 7 hasta la primera vacía), delegando la resolución de catálogo de
 * cada una a {@link ExcelRowResolver} (transacción propia por fila — necesario para poder
 * saltear filas inconsistentes sin perder el resto, ver su javadoc) y acumulando las
 * asignaciones de aula para aplicarlas en un solo batch al final. Usa {@link ImportCache}
 * para no repetir búsquedas de la misma entidad entre filas de la misma importación.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportServiceImpl implements ExcelImportService {

    private final ExcelTemplateValidator validator;
    private final ExcelRowMapper rowMapper;
    private final ExcelRowResolver rowResolver;
    private final AllocationService allocationService;

    @Override
    public ImportResultDto importExcel(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(sin nombre)";
        log.info("Iniciando importación Excel: {} - {} bytes", originalFilename, file.getSize());

        Workbook workbook = validator.validate(file);
        Sheet sheet = workbook.getSheet("Hoja1");

        int year = validator.extractYear(sheet);
        ImportCache cache = new ImportCache();

        int processedRows = 0;
        AtomicInteger periodsCreated = new AtomicInteger(0);
        AtomicInteger eventsCreated = new AtomicInteger(0);
        List<RowIssueDto> skippedRows = new ArrayList<>();
        List<RowIssueDto> rowWarnings = new ArrayList<>();

        // Se acumulan los items de asignación de todas las filas y se aplican en un solo
        // batch al final: el cuello del import (~2 min con 1300 filas) era repetir, fila por
        // fila, las mismas 4 queries (evento, aula, occurrences, asignaciones existentes) que
        // una sola vez para todo el archivo.
        List<AllocationItem> pendingAllocations = new ArrayList<>();

        for (int rowIndex = 6; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) break;

            int rowNum = rowIndex + 1;
            ExcelRowDto dto = rowMapper.map(row, rowNum);

            TermType termType = TermType.fromLabel(dto.termType())
                .orElseThrow(() -> new ExcelImportException(
                    "Unknown term type: '" + dto.termType() + "', row " + rowNum));
            LocalDate startDate = termType.startDate(year);
            LocalDate endDate = termType.endDate(year);

            // Specialty/StudyPlan/Subject/Commission/SubjectCommission/Building/Classroom son
            // catálogo cargado por fuera de esta app: se buscan (fallan si no existen), nunca
            // se crean desde el import. AcademicPeriod y RecurringEvent sí se crean acá si no
            // existen (varias filas de la planilla pueden describir el mismo evento). Una fila
            // que no resuelve contra el catálogo (dato inconsistente en el origen) se saltea y
            // se reporta en vez de abortar el import completo.
            try {
                ExcelRowResolver.ResolvedRow resolved = rowResolver.resolve(dto, termType, year, startDate, endDate, cache, periodsCreated);
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
                    rowNum, resolved.subject().name(), resolved.commission().commissionNumber(), dto.roomNumber());
            } catch (ResourceNotFoundException e) {
                skippedRows.add(new RowIssueDto(rowNum, e.getMessage()));
                log.warn("Fila {} salteada, no resuelve contra el catálogo: {}", rowNum, e.getMessage());
            }
        }

        if (!pendingAllocations.isEmpty()) {
            allocationService.reallocate(AllocationCommand.imported(pendingAllocations, "Importado de Excel"));
        }

        log.info("Importación completada: {} filas, {} períodos creados, {} eventos creados, {} filas salteadas, {} advertencias",
            processedRows, periodsCreated.get(), eventsCreated.get(), skippedRows.size(), rowWarnings.size());

        return new ImportResultDto(processedRows, periodsCreated.get(), eventsCreated.get(), skippedRows, rowWarnings);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 16; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
