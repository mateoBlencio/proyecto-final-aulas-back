package ar.edu.utn.frc.siga.excelimport.validator;

import ar.edu.utn.frc.siga.excelimport.exception.ExcelFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class ExcelTemplateValidator {

    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
        "Curso", "Comisión", "Aula", "Nombre Edificio", "Día", "Dictado",
        "Hora Comienzo", "Hora Fin", "Rango Horario", "Durac[min]",
        "Duracion[hs]", "Especialidad", "Plan", "Materia",
        "Nombre de materia", "Cantidad de Cursado"
    );

    private static final String SHEET_NAME = "Hoja1";
    private static final int HEADER_ROW_INDEX = 5;
    private static final int FIRST_DATA_ROW_INDEX = 6;
    private static final int EXPECTED_COLUMN_COUNT = 16;

    public Workbook validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("File is null or empty");
            throw new ExcelFormatException("File is empty or null");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xls") || filename.endsWith(".xlsx"))) {
            log.warn("Invalid file extension: {}", filename);
            throw new ExcelFormatException(
                "Invalid file extension: expected .xls or .xlsx, got " + filename);
        }

        Workbook workbook;
        try (InputStream is = file.getInputStream()) {
            workbook = WorkbookFactory.create(is);
        } catch (EmptyFileException e) {
            log.warn("Empty file");
            throw new ExcelFormatException("File is empty");
        } catch (NotOfficeXmlFileException | IOException e) {
            log.warn("File is not a valid Excel file: {}", e.getMessage());
            throw new ExcelFormatException("File is not a valid Excel file: " + e.getMessage());
        }

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null) {
            log.warn("Sheet '{}' not found", SHEET_NAME);
            throw new ExcelFormatException("Sheet '" + SHEET_NAME + "' not found. " +
                "The Excel file must contain a sheet named '" + SHEET_NAME + "'");
        }

        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            log.warn("Header row (row 6) not found");
            throw new ExcelFormatException("Header row (row 6) not found");
        }

        if (headerRow.getLastCellNum() < EXPECTED_COLUMN_COUNT) {
            log.warn("Header row has {} columns, expected at least {}",
                headerRow.getLastCellNum(), EXPECTED_COLUMN_COUNT);
            throw new ExcelFormatException("Header row has " + headerRow.getLastCellNum()
                + " columns, expected at least " + EXPECTED_COLUMN_COUNT);
        }

        for (int i = 0; i < EXPECTED_COLUMN_COUNT; i++) {
            Cell cell = headerRow.getCell(i);
            String actualValue = cell == null ? "" : cell.getStringCellValue().trim();
            String expectedValue = EXPECTED_HEADERS.get(i);
            if (!actualValue.equals(expectedValue)) {
                log.warn("Header mismatch at column {}: expected '{}', found '{}'",
                    i + 1, expectedValue, actualValue);
                throw new ExcelFormatException(
                    "Header mismatch at column " + (i + 1)
                        + ": expected '" + expectedValue + "', found '" + actualValue + "'");
            }
        }

        boolean hasDataRow = false;
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                break;
            }
            hasDataRow = true;
        }

        if (!hasDataRow) {
            log.warn("No data rows found in the file");
            throw new ExcelFormatException("No data rows found. " +
                "The file must contain at least one data row starting from row 7.");
        }

        return workbook;
    }

    public int extractYear(Sheet sheet) {
        Row row = sheet.getRow(3);
        if (row == null) return Year.now().getValue();
        Cell cell = row.getCell(0);
        if (cell == null || cell.getCellType() != CellType.STRING) return Year.now().getValue();
        Matcher m = Pattern.compile("Año=(\\d{4})").matcher(cell.getStringCellValue());
        return m.find() ? Integer.parseInt(m.group(1)) : Year.now().getValue();
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < EXPECTED_COLUMN_COUNT; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
