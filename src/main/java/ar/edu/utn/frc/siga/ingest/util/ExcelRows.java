package ar.edu.utn.frc.siga.ingest.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

public final class ExcelRows {

    private ExcelRows() {
    }

    public static boolean isEmpty(Row row, int columnCount) {
        if (row == null) return true;
        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
