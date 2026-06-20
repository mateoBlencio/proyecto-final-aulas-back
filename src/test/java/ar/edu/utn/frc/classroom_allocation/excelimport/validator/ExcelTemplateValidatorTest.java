package ar.edu.utn.frc.classroom_allocation.excelimport.validator;

import ar.edu.utn.frc.classroom_allocation.excelimport.ExcelTestFactory;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelFormatException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;

@ExtendWith(MockitoExtension.class)
class ExcelTemplateValidatorTest {

    private ExcelTemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ExcelTemplateValidator();
    }

    private void mockInputStream(MultipartFile file, byte[] data) {
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validate_shouldPassWhenFileIsValid() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        mockInputStream(file, ExcelTestFactory.validXlsx(1));

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_shouldPassWhenFileIsXls() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xls");
        mockInputStream(file, ExcelTestFactory.validXls(1));

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenFileIsNotExcel() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.csv");

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenSheetHoja1Missing() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");

        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        wb.createSheet("OtroNombre");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { wb.write(baos); wb.close(); } catch (Exception e) { throw new RuntimeException(e); }

        mockInputStream(file, baos.toByteArray());

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenHeaderRowMissing() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");

        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        wb.createSheet("Hoja1");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { wb.write(baos); wb.close(); } catch (Exception e) { throw new RuntimeException(e); }

        mockInputStream(file, baos.toByteArray());

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenHeaderNameMismatch() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        mockInputStream(file, ExcelTestFactory.badHeaders());

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenNoDataRows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        mockInputStream(file, ExcelTestFactory.noDataRows());

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldPassWhenHeadersHaveTrailingSpaces() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");

        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Hoja1");
        sheet.createRow(3).createCell(0).setCellValue("Año=2026");

        String[] headersWithSpaces = {
            "Curso ", "Comisión ", "Aula ", "Nombre Edificio ", "Día       ",
            "Dictado ", "Hora Comienzo ", "Hora Fin ", "Rango Horario ", "Durac[min] ",
            "Duracion[hs] ", "Especialidad ", "Plan ", "Materia ",
            "Nombre de materia ", "Cantidad de Cursado "
        };
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(5);
        for (int i = 0; i < 16; i++) {
            headerRow.createCell(i).setCellValue(headersWithSpaces[i]);
        }
        sheet.createRow(6).createCell(0).setCellValue("1C1");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { wb.write(baos); wb.close(); } catch (Exception e) { throw new RuntimeException(e); }

        mockInputStream(file, baos.toByteArray());

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_shouldPassWhenDataRowFollowedByEmptyRow() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");

        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Hoja1");
        sheet.createRow(3).createCell(0).setCellValue("Año=2026");

        String[] headers = {
            "Curso", "Comisión", "Aula", "Nombre Edificio", "Día", "Dictado",
            "Hora Comienzo", "Hora Fin", "Rango Horario", "Durac[min]",
            "Duracion[hs]", "Especialidad", "Plan", "Materia",
            "Nombre de materia", "Cantidad de Cursado"
        };
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(5);
        for (int i = 0; i < 16; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(6);
        dataRow.createCell(0).setCellValue("1C1");
        dataRow.createCell(1).setCellValue(10.0);
        dataRow.createCell(2).setCellValue(513.0);
        dataRow.createCell(3).setCellValue("Edif");
        dataRow.createCell(4).setCellValue("Jueves");
        dataRow.createCell(5).setCellValue("1 Cuat.");
        dataRow.createCell(6).setCellValue(800.0);
        dataRow.createCell(7).setCellValue(1540.0);
        dataRow.createCell(11).setCellValue(31.0);
        dataRow.createCell(12).setCellValue(2023.0);
        dataRow.createCell(13).setCellValue(104.0);
        dataRow.createCell(14).setCellValue("Materia");
        dataRow.createCell(15).setCellValue(30.0);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { wb.write(baos); wb.close(); } catch (Exception e) { throw new RuntimeException(e); }

        mockInputStream(file, baos.toByteArray());

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_shouldThrowWhenFileIsEmpty() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(ExcelFormatException.class, () -> validator.validate(file));
    }

    @Test
    void validate_shouldReturnWorkbookOnSuccess() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        mockInputStream(file, ExcelTestFactory.validXlsx(1));

        assertNotNull(validator.validate(file));
    }
}
