package ar.edu.utn.frc.siga.excelimport.mapper;

import ar.edu.utn.frc.siga.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.siga.excelimport.exception.ExcelImportException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

@Component
public class ExcelRowMapper {

    public ExcelRowDto map(Row row, int rowNum) {
        String courseCode = getStringCellValue(row, 0, "Curso", rowNum);
        Integer commissionNumber = getNumericIntValue(row, 1, "Comisión", rowNum);
        String roomNumber = getAulaValue(row, 2, rowNum);
        String buildingName = getStringCellValue(row, 3, "Nombre Edificio", rowNum);
        DayOfWeek dayOfWeek = parseDayOfWeek(getStringCellValue(row, 4, "Día", rowNum), rowNum);
        String termType = getStringCellValue(row, 5, "Dictado", rowNum);
        LocalTime startTime = parseHHMM(getNumericIntValue(row, 6, "Hora Comienzo", rowNum));
        LocalTime endTime = parseHHMM(getNumericIntValue(row, 7, "Hora Fin", rowNum));
        Integer durationMinutes = getOptionalNumericIntValue(row, 9);
        Integer specialtyCode = getNumericIntValue(row, 11, "Especialidad", rowNum);
        Integer studyPlanCode = getNumericIntValue(row, 12, "Plan", rowNum);
        Integer subjectCode = getNumericIntValue(row, 13, "Materia", rowNum);
        String subjectName = getStringCellValue(row, 14, "Nombre de materia", rowNum);
        Integer enrolledCount = getNumericIntValue(row, 15, "Cantidad de Cursado", rowNum);

        return new ExcelRowDto(
            courseCode, commissionNumber, roomNumber, buildingName,
            dayOfWeek, termType, startTime, endTime, durationMinutes,
            specialtyCode, studyPlanCode, subjectCode, subjectName, enrolledCount
        );
    }

    private DayOfWeek parseDayOfWeek(String day, int rowNum) {
        return switch (day.trim()) {
            case "Lunes" -> DayOfWeek.MONDAY;
            case "Martes" -> DayOfWeek.TUESDAY;
            case "Miércoles", "Miercoles" -> DayOfWeek.WEDNESDAY;
            case "Jueves" -> DayOfWeek.THURSDAY;
            case "Viernes" -> DayOfWeek.FRIDAY;
            case "Sábado", "Sabado" -> DayOfWeek.SATURDAY;
            case "Domingo" -> DayOfWeek.SUNDAY;
            default -> throw new ExcelImportException(
                "Unknown day of week: '" + day + "', row " + rowNum);
        };
    }

    private LocalTime parseHHMM(int hhmm) {
        return LocalTime.of(hhmm / 100, hhmm % 100);
    }

    private String getStringCellValue(Row row, int cellIndex, String columnName, int rowNum) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new ExcelImportException(
                "Column '" + columnName + "' is required but was blank, row " + rowNum);
        }
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private Integer getNumericIntValue(Row row, int cellIndex, String columnName, int rowNum) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new ExcelImportException(
                "Column '" + columnName + "' is required but was blank, row " + rowNum);
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return (int) cell.getNumericCellValue();
        }
        if (cell.getCellType() != CellType.NUMERIC) {
            throw new ExcelImportException(
                "Column '" + columnName + "' must be numeric, row " + rowNum);
        }
        return (int) cell.getNumericCellValue();
    }

    private Integer getOptionalNumericIntValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return (int) cell.getNumericCellValue();
    }

    private String getAulaValue(Row row, int cellIndex, int rowNum) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new ExcelImportException(
                "Column 'Aula' is required but was blank, row " + rowNum);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((int) cell.getNumericCellValue());
        }
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
}
