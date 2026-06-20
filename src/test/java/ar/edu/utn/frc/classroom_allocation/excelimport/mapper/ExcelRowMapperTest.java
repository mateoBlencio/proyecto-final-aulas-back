package ar.edu.utn.frc.classroom_allocation.excelimport.mapper;

import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class ExcelRowMapperTest {

    private ExcelRowMapper mapper;
    private XSSFWorkbook workbook;
    private Row row;

    @BeforeEach
    void setUp() {
        mapper = new ExcelRowMapper();
        workbook = new XSSFWorkbook();
        row = workbook.createSheet().createRow(0);
    }

    @Test
    void map_shouldReturnDtoWhenRowIsValid() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif. Dr. Gallardo");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(8).setCellValue("18:15-19:45");
        row.createCell(9).setCellValue(90.0);
        row.createCell(10).setCellValue(1.5);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Ingeniería Civil I");
        row.createCell(15).setCellValue(45.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertAll(
            () -> assertEquals("1C1", dto.courseCode()),
            () -> assertEquals(10, dto.commissionNumber()),
            () -> assertEquals("513", dto.roomNumber()),
            () -> assertEquals("Edif. Dr. Gallardo", dto.buildingName()),
            () -> assertEquals("Jueves", dto.dayOfWeek()),
            () -> assertEquals("1 Cuat.", dto.termType()),
            () -> assertEquals(800, dto.startTime()),
            () -> assertEquals(1540, dto.endTime()),
            () -> assertEquals(90, dto.durationMinutes()),
            () -> assertEquals(31, dto.specialtyCode()),
            () -> assertEquals(2023, dto.studyPlanCode()),
            () -> assertEquals(104, dto.subjectCode()),
            () -> assertEquals("Ingeniería Civil I", dto.subjectName()),
            () -> assertEquals(45, dto.enrolledCount())
        );
    }

    @Test
    void map_shouldConvertNumericAulaToString() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif. Dr. Gallardo");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(9).setCellValue(90.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals("513", dto.roomNumber());
    }

    @Test
    void map_shouldConvertHHMMToCorrectIntValues() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif. Dr. Gallardo");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1720.0);
        row.createCell(9).setCellValue(90.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals(800, dto.startTime());
        assertEquals(1720, dto.endTime());
    }

    @Test
    void map_shouldAllowNullDurationMinutes() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif. Dr. Gallardo");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertNull(dto.durationMinutes());
    }

    @Test
    void map_shouldTrimStringValues() {
        row.createCell(0).setCellValue("1C1   ");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("  Edif. Dr. Gallardo  ");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(9).setCellValue(90.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("  Materia  ");
        row.createCell(15).setCellValue(30.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals("1C1", dto.courseCode());
        assertEquals("Edif. Dr. Gallardo", dto.buildingName());
        assertEquals("Materia", dto.subjectName());
    }

    @Test
    void map_shouldThrowWhenCursoIsBlank() {
        row.createCell(1).setCellValue(10.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenComisionIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenDictadoIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenDiaIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenNombreMateriaIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenEdificioIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenEspecialidadIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenHoraComienzoIsNotNumeric() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue("ochocientos");
        row.createCell(7).setCellValue(1540.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldThrowWhenHoraFinIsBlank() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);
        assertThrows(ExcelImportException.class, () -> mapper.map(row, 7));
    }

    @Test
    void map_shouldAcceptZeroCantidadInscriptos() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(1540.0);
        row.createCell(9).setCellValue(90.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(0.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals(0, dto.enrolledCount());
    }

    @Test
    void map_shouldHandleFormulaCells() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");

        Cell startTimeCell = row.createCell(6);
        startTimeCell.setCellFormula("800+0");
        row.createCell(7).setCellValue(1540.0);

        row.createCell(9).setCellValue(90.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);

        org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals(800, dto.startTime());
    }

    @Test
    void map_shouldReturnDtoWithSingleDigitHHMM() {
        row.createCell(0).setCellValue("1C1");
        row.createCell(1).setCellValue(10.0);
        row.createCell(2).setCellValue(513.0);
        row.createCell(3).setCellValue("Edif. Dr. Gallardo");
        row.createCell(4).setCellValue("Jueves");
        row.createCell(5).setCellValue("1 Cuat.");
        row.createCell(6).setCellValue(800.0);
        row.createCell(7).setCellValue(900.0);
        row.createCell(9).setCellValue(60.0);
        row.createCell(11).setCellValue(31.0);
        row.createCell(12).setCellValue(2023.0);
        row.createCell(13).setCellValue(104.0);
        row.createCell(14).setCellValue("Materia");
        row.createCell(15).setCellValue(30.0);

        ExcelRowDto dto = mapper.map(row, 7);

        assertEquals(800, dto.startTime());
        assertEquals(900, dto.endTime());
    }
}
