package ar.edu.utn.frc.classroom_allocation.excelimport.mapper;

import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
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
        String dayOfWeek = getStringCellValue(row, 4, "Día", rowNum);
        String termType = getStringCellValue(row, 5, "Dictado", rowNum);
        Integer startTime = getNumericIntValue(row, 6, "Hora Comienzo", rowNum);
        Integer endTime = getNumericIntValue(row, 7, "Hora Fin", rowNum);
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
