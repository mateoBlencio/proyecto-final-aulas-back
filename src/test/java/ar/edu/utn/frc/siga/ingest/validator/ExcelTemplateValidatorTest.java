package ar.edu.utn.frc.siga.ingest.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.ingest.exception.InvalidFileFormatException;
import java.time.Year;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
