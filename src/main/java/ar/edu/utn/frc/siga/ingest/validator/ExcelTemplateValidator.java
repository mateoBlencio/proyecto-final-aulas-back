package ar.edu.utn.frc.siga.ingest.validator;

import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import ar.edu.utn.frc.siga.ingest.util.ExcelRows;
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
            log.warn("Archivo nulo o vacío");
            throw new InvalidFileFormatException("File is empty or null");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xls") || filename.endsWith(".xlsx"))) {
            log.warn("Extensión de archivo inválida: {}", filename);
            throw new InvalidFileFormatException(
                "Invalid file extension: expected .xls or .xlsx, got " + filename);
        }

        Workbook workbook;
        try (InputStream is = file.getInputStream()) {
            workbook = WorkbookFactory.create(is);
        } catch (EmptyFileException e) {
            log.warn("Archivo vacío");
            throw new InvalidFileFormatException("File is empty");
        } catch (NotOfficeXmlFileException | IOException e) {
            log.warn("El archivo no es un Excel válido: {}", e.getMessage());
            throw new InvalidFileFormatException("File is not a valid Excel file: " + e.getMessage());
        }

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null) {
            log.warn("Hoja '{}' no encontrada", SHEET_NAME);
            throw new InvalidFileFormatException("Sheet '" + SHEET_NAME + "' not found. " +
                "The Excel file must contain a sheet named '" + SHEET_NAME + "'");
        }

        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            log.warn("Fila de encabezado (fila 6) no encontrada");
            throw new InvalidFileFormatException("Header row (row 6) not found");
        }

        if (headerRow.getLastCellNum() < EXPECTED_COLUMN_COUNT) {
            log.warn("La fila de encabezado tiene {} columnas, se esperaban al menos {}",
                headerRow.getLastCellNum(), EXPECTED_COLUMN_COUNT);
            throw new InvalidFileFormatException("Header row has " + headerRow.getLastCellNum()
                + " columns, expected at least " + EXPECTED_COLUMN_COUNT);
        }

        for (int i = 0; i < EXPECTED_COLUMN_COUNT; i++) {
            Cell cell = headerRow.getCell(i);
            String actualValue = cell == null ? "" : cell.getStringCellValue().trim();
            String expectedValue = EXPECTED_HEADERS.get(i);
            if (!actualValue.equals(expectedValue)) {
                log.warn("Encabezado no coincide en columna {}: se esperaba '{}', se encontró '{}'",
                    i + 1, expectedValue, actualValue);
                throw new InvalidFileFormatException(
                    "Header mismatch at column " + (i + 1)
                        + ": expected '" + expectedValue + "', found '" + actualValue + "'");
            }
        }

        boolean hasDataRow = false;
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (ExcelRows.isEmpty(row, EXPECTED_COLUMN_COUNT)) {
                break;
            }
            hasDataRow = true;
        }

        if (!hasDataRow) {
            log.warn("No se encontraron filas de datos en el archivo");
            throw new InvalidFileFormatException("No data rows found. " +
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
}
