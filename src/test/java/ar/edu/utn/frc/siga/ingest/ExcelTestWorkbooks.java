package ar.edu.utn.frc.siga.ingest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import lombok.Builder;

public final class ExcelTestWorkbooks {

    public static final List<String> HEADERS = List.of(
        "Curso", "Comisión", "Aula", "Nombre Edificio", "Día", "Dictado",
        "Hora Comienzo", "Hora Fin", "Rango Horario", "Durac[min]",
        "Duracion[hs]", "Especialidad", "Plan", "Materia",
        "Nombre de materia", "Cantidad de Cursado"
    );

    public static final String SHEET_NAME = "Hoja1";
    private static final int YEAR_ROW_INDEX = 3;
    private static final int HEADER_ROW_INDEX = 5;
    private static final int FIRST_DATA_ROW_INDEX = 6;

    private final XSSFWorkbook workbook;
    private final Sheet sheet;
    private int nextDataRowIndex = FIRST_DATA_ROW_INDEX;

    private ExcelTestWorkbooks() {
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet(SHEET_NAME);
    }

    public static ExcelTestWorkbooks validTemplate() {
        return validTemplate(2026);
    }

    public static ExcelTestWorkbooks validTemplate(int year) {
        ExcelTestWorkbooks fixture = new ExcelTestWorkbooks();
        fixture.writeYearRow(year);
        fixture.writeHeaderRow(HEADERS);
        return fixture;
    }

    public static ExcelTestWorkbooks blankWorkbook() {
        return new ExcelTestWorkbooks();
    }


    public ExcelTestWorkbooks withValidDataRow() {
        return withDataRow(DataRow.defaultRow());
    }

    public ExcelTestWorkbooks withDataRow(DataRow row) {
        Row r = sheet.createRow(nextDataRowIndex++);
        setString(r, 0, row.courseCode());
        setNumeric(r, 1, row.commissionNumber());
        setAula(r, 2, row.roomNumber());
        setString(r, 3, row.buildingName());
        setString(r, 4, row.day());
        setString(r, 5, row.termType());
        setNumeric(r, 6, row.startTime());
        setNumeric(r, 7, row.endTime());
        if (row.durationMinutes() != null) {
            setNumeric(r, 9, row.durationMinutes());
        }
        setNumeric(r, 11, row.specialtyCode());
        setNumeric(r, 12, row.studyPlanCode());
        setNumeric(r, 13, row.subjectCode());
        setString(r, 14, row.subjectName());
        setNumeric(r, 15, row.enrolledCount());
        return this;
    }

    public ExcelTestWorkbooks withEmptyRow() {
        sheet.createRow(nextDataRowIndex++);
        return this;
    }


    public ExcelTestWorkbooks renameSheet(String newName) {
        workbook.setSheetName(workbook.getSheetIndex(sheet), newName);
        return this;
    }

    public ExcelTestWorkbooks withHeader(int columnIndex, String value) {
        setString(sheet.getRow(HEADER_ROW_INDEX), columnIndex, value);
        return this;
    }

    public ExcelTestWorkbooks withoutYearRow() {
        Row yearRow = sheet.getRow(YEAR_ROW_INDEX);
        if (yearRow != null) {
            sheet.removeRow(yearRow);
        }
        return this;
    }

    public ExcelTestWorkbooks withYearCellValue(String rawValue) {
        setString(sheet.getRow(YEAR_ROW_INDEX), 0, rawValue);
        return this;
    }

    public ExcelTestWorkbooks withBlankCell(int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row.getCell(columnIndex);
        if (cell != null) {
            row.removeCell(cell);
        }
        return this;
    }

    public ExcelTestWorkbooks withStringCell(int rowIndex, int columnIndex, String value) {
        setString(sheet.getRow(rowIndex), columnIndex, value);
        return this;
    }

    public ExcelTestWorkbooks withNumericCell(int rowIndex, int columnIndex, double value) {
        setNumeric(sheet.getRow(rowIndex), columnIndex, value);
        return this;
    }

    public ExcelTestWorkbooks truncateHeaderColumns(int columnCount) {
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        for (int i = headerRow.getLastCellNum() - 1; i >= columnCount; i--) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                headerRow.removeCell(cell);
            }
        }
        return this;
    }


    public Workbook workbook() {
        return workbook;
    }

    public Sheet sheet() {
        return sheet;
    }

    public Row dataRow(int offsetFromFirst) {
        return sheet.getRow(FIRST_DATA_ROW_INDEX + offsetFromFirst);
    }

    public MockMultipartFile toMultipartFile() {
        return toMultipartFile("import.xlsx");
    }

    public MockMultipartFile toMultipartFile(String filename) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private void writeYearRow(int year) {
        Row row = sheet.createRow(YEAR_ROW_INDEX);
        setString(row, 0, "Año=" + year);
    }

    private void writeHeaderRow(List<String> headers) {
        Row row = sheet.createRow(HEADER_ROW_INDEX);
        for (int i = 0; i < headers.size(); i++) {
            setString(row, i, headers.get(i));
        }
    }

    private void setString(Row row, int columnIndex, String value) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(value);
    }

    private void setNumeric(Row row, int columnIndex, Number value) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(value.doubleValue());
    }

    private void setAula(Row row, int columnIndex, Object value) {
        if (value instanceof Number n) {
            setNumeric(row, columnIndex, n);
        } else {
            setString(row, columnIndex, String.valueOf(value));
        }
    }

    @Builder(toBuilder = true)
    public record DataRow(
        String courseCode,
        Integer commissionNumber,
        Object roomNumber,
        String buildingName,
        String day,
        String termType,
        Integer startTime,
        Integer endTime,
        Integer durationMinutes,
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        String subjectName,
        Integer enrolledCount
    ) {
        public static DataRow defaultRow() {
            return DataRow.builder()
                .courseCode("6301")
                .commissionNumber(1)
                .roomNumber(105)
                .buildingName("Edificio Central")
                .day("Lunes")
                .termType("Anual")
                .startTime(1830)
                .endTime(2000)
                .durationMinutes(null)
                .specialtyCode(1)
                .studyPlanCode(1)
                .subjectCode(100)
                .subjectName("Análisis Matemático")
                .enrolledCount(30)
                .build();
        }
    }
}
