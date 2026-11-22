package ar.edu.utn.frc.siga.ingest.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Year;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelTemplateValidator")
class ExcelTemplateValidatorTest {

    private final ExcelTemplateValidator validator = new ExcelTemplateValidator();

    @Test
    @DisplayName("plantilla válida con al menos una fila de datos → pasa y devuelve el workbook")
    void plantillaValidaPasa() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow().toMultipartFile();

        Workbook workbook = validator.validate(file);

        assertThat(workbook).isNotNull();
        assertThat(workbook.getSheet(ExcelTestWorkbooks.SHEET_NAME)).isNotNull();
    }

    @Test
    @DisplayName("archivo null → InvalidFileFormatException")
    void archivoNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(InvalidFileFormatException.class);
    }

    @Test
    @DisplayName("archivo vacío (sin bytes) → InvalidFileFormatException")
    void archivoVacio() {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class);
    }

    @Test
    @DisplayName("extensión incorrecta → InvalidFileFormatException con el nombre de archivo recibido")
    void extensionIncorrecta() {
        MockMultipartFile file = new MockMultipartFile("file", "import.txt",
            "text/plain", "contenido".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("expected .xls or .xlsx")
            .hasMessageContaining("import.txt");
    }

    @Test
    @DisplayName("nombre de archivo null → InvalidFileFormatException (falla la validación de extensión)")
    void nombreArchivoNull() {
        MockMultipartFile file = new MockMultipartFile("file", null,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "contenido".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class);
    }

    @Test
    @DisplayName("contenido no es un Excel válido → InvalidFileFormatException")
    void contenidoNoExcel() {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "esto no es un archivo excel".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class);
    }

    @Test
    @DisplayName("sin hoja 'Hoja1' → InvalidFileFormatException que nombra la hoja esperada")
    void sinHojaEsperada() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .renameSheet("OtraHoja")
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("Hoja1");
    }

    @Test
    @DisplayName("header alterado → InvalidFileFormatException que nombra el header esperado y el encontrado")
    void headerAlterado() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withHeader(0, "CursoX")
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("column 1")
            .hasMessageContaining("expected 'Curso'")
            .hasMessageContaining("found 'CursoX'");
    }

    @Test
    @DisplayName("ING-02: header numérico → InvalidFileFormatException (no IllegalStateException de POI) que nombra columna, esperado y encontrado")
    void headerNumerico() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withNumericCell(5, 0, 123)
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("column 1")
            .hasMessageContaining("expected 'Curso'")
            .hasMessageContaining("found '123'");
    }

    @Test
    @DisplayName("ING-02: si la validación del template falla, el Workbook abierto se cierra")
    void cierraElWorkbookCuandoFallaLaValidacion() throws IOException {
        Workbook spied = spy(ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withHeader(0, "CursoX").workbook());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow().toMultipartFile();

        try (MockedStatic<WorkbookFactory> factory = mockStatic(WorkbookFactory.class)) {
            factory.when(() -> WorkbookFactory.create(any(InputStream.class))).thenReturn(spied);

            assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(InvalidFileFormatException.class);
        }

        verify(spied).close();
    }

    @Test
    @DisplayName("ING-02: si la validación pasa, el Workbook se devuelve abierto para que lo cierre el llamador")
    void noCierraElWorkbookCuandoLaValidacionPasa() throws IOException {
        Workbook spied = spy(ExcelTestWorkbooks.validTemplate().withValidDataRow().workbook());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow().toMultipartFile();

        try (MockedStatic<WorkbookFactory> factory = mockStatic(WorkbookFactory.class)) {
            factory.when(() -> WorkbookFactory.create(any(InputStream.class))).thenReturn(spied);

            assertThat(validator.validate(file)).isSameAs(spied);
        }

        verify(spied, never()).close();
    }

    @Test
    @DisplayName("menos de 16 columnas en el header → InvalidFileFormatException")
    void menosDe16Columnas() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .truncateHeaderColumns(10)
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("expected at least 16");
    }

    @Test
    @DisplayName("sin filas de datos → InvalidFileFormatException")
    void sinFilasDeDatos() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(InvalidFileFormatException.class)
            .hasMessageContaining("No data rows found");
    }

    @Test
    @DisplayName("extractYear: fila 'Año=2026' bien formada → 2026")
    void extractYearFeliz() {
        ExcelTestWorkbooks fixture = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow();
        Sheet sheet = fixture.sheet();

        assertThat(validator.extractYear(sheet)).isEqualTo(2026);
    }

    @Test
    @DisplayName("extractYear: sin fila de año → fallback al año actual")
    void extractYearSinFila() {
        ExcelTestWorkbooks fixture = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow()
            .withoutYearRow();

        assertThat(validator.extractYear(fixture.sheet())).isEqualTo(Year.now().getValue());
    }

    @Test
    @DisplayName("extractYear: fila de año mal formada (sin patrón Año=NNNN) → fallback al año actual")
    void extractYearMalFormada() {
        ExcelTestWorkbooks fixture = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow()
            .withYearCellValue("esto no tiene el patrón esperado");

        assertThat(validator.extractYear(fixture.sheet())).isEqualTo(Year.now().getValue());
    }
}
