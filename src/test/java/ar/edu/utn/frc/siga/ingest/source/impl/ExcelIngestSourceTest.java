package ar.edu.utn.frc.siga.ingest.source.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks.DataRow;
import ar.edu.utn.frc.siga.ingest.exception.InvalidRowException;
import ar.edu.utn.frc.siga.ingest.mapper.ExcelRowMapper;
import ar.edu.utn.frc.siga.ingest.source.IngestSource;
import ar.edu.utn.frc.siga.ingest.validator.ExcelTemplateValidator;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelIngestSource")
class ExcelIngestSourceTest {

    @Mock
    private ExcelTemplateValidator validator;

    private final ExcelRowMapper rowMapper = new ExcelRowMapper();

    private ExcelIngestSource source() {
        return new ExcelIngestSource(validator, rowMapper);
    }

    @Test
    @DisplayName("ING-02: parse cierra el Workbook cuando termina bien y devuelve las filas con su número y el año")
    void cierraElWorkbookAlTerminar() throws IOException {
        Workbook workbook = spy(ExcelTestWorkbooks.validTemplate(2027).withValidDataRow().workbook());
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/octet-stream", new byte[0]);
        when(validator.validate(file)).thenReturn(workbook);
        when(validator.extractYear(workbook.getSheet(ExcelTestWorkbooks.SHEET_NAME))).thenReturn(2027);

        IngestSource.ParsedContent parsed = source().parse(file);

        assertThat(parsed.year()).isEqualTo(2027);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().rowNumber()).isEqualTo(7);
        verify(workbook).close();
    }

    @Test
    @DisplayName("ING-02: parse cierra el Workbook también cuando una fila es inválida")
    void cierraElWorkbookCuandoUnaFilaEsInvalida() throws IOException {
        Workbook workbook = spy(ExcelTestWorkbooks.validTemplate(2026)
            .withDataRow(DataRow.defaultRow().toBuilder().startTime(2575).build()).workbook());
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/octet-stream", new byte[0]);
        when(validator.validate(file)).thenReturn(workbook);
        when(validator.extractYear(workbook.getSheet(ExcelTestWorkbooks.SHEET_NAME))).thenReturn(2026);

        assertThatThrownBy(() -> source().parse(file))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Comienzo");

        verify(workbook).close();
    }
}
