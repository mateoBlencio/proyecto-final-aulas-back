package ar.edu.utn.frc.siga.excelimport.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.excelimport.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.excelimport.exception.ExcelFormatException;
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
    @DisplayName("archivo null → ExcelFormatException")
    void archivoNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(ExcelFormatException.class);
    }

    @Test
    @DisplayName("archivo vacío (sin bytes) → ExcelFormatException")
    void archivoVacio() {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class);
    }

    @Test
    @DisplayName("extensión incorrecta → ExcelFormatException con el nombre de archivo recibido")
    void extensionIncorrecta() {
        MockMultipartFile file = new MockMultipartFile("file", "import.txt",
            "text/plain", "contenido".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class)
            .hasMessageContaining("expected .xls or .xlsx")
            .hasMessageContaining("import.txt");
    }

    @Test
    @DisplayName("nombre de archivo null → ExcelFormatException (falla la validación de extensión)")
    void nombreArchivoNull() {
        MockMultipartFile file = new MockMultipartFile("file", null,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "contenido".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class);
    }

    @Test
    @DisplayName("contenido no es un Excel válido → ExcelFormatException")
    void contenidoNoExcel() {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "esto no es un archivo excel".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class);
    }

    @Test
    @DisplayName("sin hoja 'Hoja1' → ExcelFormatException que nombra la hoja esperada")
    void sinHojaEsperada() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .renameSheet("OtraHoja")
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class)
            .hasMessageContaining("Hoja1");
    }

    @Test
    @DisplayName("header alterado → ExcelFormatException que nombra el header esperado y el encontrado")
    void headerAlterado() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withHeader(0, "CursoX")
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class)
            .hasMessageContaining("column 1")
            .hasMessageContaining("expected 'Curso'")
            .hasMessageContaining("found 'CursoX'");
    }

    @Test
    @DisplayName("menos de 16 columnas en el header → ExcelFormatException")
    void menosDe16Columnas() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .truncateHeaderColumns(10)
            .toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class)
            .hasMessageContaining("expected at least 16");
    }

    @Test
    @DisplayName("sin filas de datos → ExcelFormatException")
    void sinFilasDeDatos() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().toMultipartFile();

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(ExcelFormatException.class)
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
