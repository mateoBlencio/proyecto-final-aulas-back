package ar.edu.utn.frc.classroom_allocation.excelimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelTestFactory {

    private static final String[] HEADERS = {
        "Curso", "Comisión", "Aula", "Nombre Edificio", "Día", "Dictado",
        "Hora Comienzo", "Hora Fin", "Rango Horario", "Durac[min]",
        "Duracion[hs]", "Especialidad", "Plan", "Materia",
        "Nombre de materia", "Cantidad de Cursado"
    };

    public static byte[] validXlsx(int dataRows) {
        return buildBytes(new XSSFWorkbook(), dataRows);
    }

    public static byte[] validXls(int dataRows) {
        return buildBytes(new HSSFWorkbook(), dataRows);
    }

    public static byte[] badHeaders() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        Row headerRow = sheet.createRow(5);
        for (int i = 0; i < 16; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(i == 0 ? "CursoXXX" : HEADERS[i]);
        }
        return toBytes(wb);
    }

    public static byte[] unknownTermType() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        createHeaderRow(sheet);
        Row row = sheet.createRow(6);
        createValidDataRow(row, "1C1", 10, 513, "Edif. Dr. Gallardo",
            "Jueves", "Verano", 800, 1540, 90, 31, 2023, 104, "Materia", 30);
        return toBytes(wb);
    }

    public static byte[] csvBytes() {
        return "curso,comision\n1C1,10\n".getBytes();
    }

    public static byte[] duplicateCommissions() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        createHeaderRow(sheet);

        Row row1 = sheet.createRow(6);
        createValidDataRow(row1, "1H10", 10, 513, "Edif. Dr. Gallardo",
            "Jueves", "1 Cuat.", 800, 1540, 90, 31, 2023, 104, "Algebra", 30);

        Row row2 = sheet.createRow(7);
        createValidDataRow(row2, "1H10", 11, 513, "Edif. Dr. Gallardo",
            "Jueves", "1 Cuat.", 800, 1540, 90, 31, 2023, 104, "Algebra", 25);

        return toBytes(wb);
    }

    public static byte[] unknownBuilding() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        createHeaderRow(sheet);
        Row row = sheet.createRow(6);
        createValidDataRow(row, "1C1", 10, 513, "Edificio Inexistente",
            "Jueves", "1 Cuat.", 800, 1540, 90, 31, 2023, 104, "Materia", 30);
        return toBytes(wb);
    }

    public static byte[] noDataRows() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        createHeaderRow(sheet);
        return toBytes(wb);
    }

    private static byte[] buildBytes(Workbook wb, int dataRows) {
        Sheet sheet = wb.createSheet("Hoja1");
        createAñoRow(sheet);
        createHeaderRow(sheet);

        for (int i = 0; i < dataRows; i++) {
            Row row = sheet.createRow(6 + i);
            createValidDataRow(row, "1C1", 10 + i, 513 + i, "Edif. Dr. Gallardo",
                "Jueves", "1 Cuat.", 800, 1540, 90, 31, 2023, 104, "Materia", 30 + i);
        }
        return toBytes(wb);
    }

    private static void createAñoRow(Sheet sheet) {
        Row row = sheet.createRow(3);
        row.createCell(0).setCellValue("Año=2026");
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(5);
        for (int i = 0; i < 16; i++) {
            headerRow.createCell(i).setCellValue(HEADERS[i]);
        }
    }

    private static void createValidDataRow(Row row, String curso, int comision, int aula,
                                            String edificio, String dia, String dictado,
                                            int horaInicio, int horaFin, int duracion,
                                            int especialidad, int plan, int materia,
                                            String nombreMateria, int cantidad) {
        row.createCell(0).setCellValue(curso);
        row.createCell(1).setCellValue((double) comision);
        row.createCell(2).setCellValue((double) aula);
        row.createCell(3).setCellValue(edificio);
        row.createCell(4).setCellValue(dia);
        row.createCell(5).setCellValue(dictado);
        row.createCell(6).setCellValue((double) horaInicio);
        row.createCell(7).setCellValue((double) horaFin);
        row.createCell(8).setCellValue(duracion + "-" + (duracion + 90));
        row.createCell(9).setCellValue((double) duracion);
        row.createCell(10).setCellValue((double) (duracion / 60.0));
        row.createCell(11).setCellValue((double) especialidad);
        row.createCell(12).setCellValue((double) plan);
        row.createCell(13).setCellValue((double) materia);
        row.createCell(14).setCellValue(nombreMateria);
        row.createCell(15).setCellValue((double) cantidad);
    }

    private static byte[] toBytes(Workbook wb) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            wb.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test Excel", e);
        }
    }
}
