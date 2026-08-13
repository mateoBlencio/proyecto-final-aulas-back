package ar.edu.utn.frc.siga.ingest.source.impl;

import ar.edu.utn.frc.siga.ingest.dto.RowDto;
import ar.edu.utn.frc.siga.ingest.dto.ImportedRow;
import ar.edu.utn.frc.siga.ingest.mapper.ExcelRowMapper;
import ar.edu.utn.frc.siga.ingest.source.IngestSource;
import ar.edu.utn.frc.siga.ingest.util.ExcelRows;
import ar.edu.utn.frc.siga.ingest.validator.ExcelTemplateValidator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ExcelIngestSource implements IngestSource {

    private static final int FIRST_DATA_ROW_INDEX = 6;
    private static final int COLUMN_COUNT = 16;
    private static final String SHEET_NAME = "Hoja1";

    private final ExcelTemplateValidator validator;
    private final ExcelRowMapper rowMapper;

    @Override
    public boolean supports(String filename) {
        return filename != null && (filename.endsWith(".xls") || filename.endsWith(".xlsx"));
    }

    @Override
    public ParsedContent parse(MultipartFile file) {
        Workbook workbook = validator.validate(file);
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        int year = validator.extractYear(sheet);

        List<ImportedRow> rows = new ArrayList<>();
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (ExcelRows.isEmpty(row, COLUMN_COUNT)) break;

            int rowNum = rowIndex + 1;
            RowDto dto = rowMapper.map(row, rowNum);
            rows.add(new ImportedRow(rowNum, dto));
        }

        return new ParsedContent(rows, year);
    }
}
