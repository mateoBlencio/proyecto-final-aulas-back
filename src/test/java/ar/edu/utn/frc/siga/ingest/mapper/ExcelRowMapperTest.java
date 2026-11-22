package ar.edu.utn.frc.siga.ingest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks.DataRow;
import ar.edu.utn.frc.siga.ingest.dto.RowDto;
import ar.edu.utn.frc.siga.ingest.exception.InvalidRowException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.apache.poi.ss.usermodel.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelRowMapper")
class ExcelRowMapperTest {

    private final ExcelRowMapper mapper = new ExcelRowMapper();

    @ParameterizedTest(name = "día ''{0}'' → {1}")
    @DisplayName("los 7 días de la semana en español, con y sin tilde, mapean al DayOfWeek correcto")
    @CsvSource({
        "Lunes, MONDAY",
        "Martes, TUESDAY",
        "Miércoles, WEDNESDAY",
        "Miercoles, WEDNESDAY",
        "Jueves, THURSDAY",
        "Viernes, FRIDAY",
        "Sábado, SATURDAY",
        "Sabado, SATURDAY",
        "Domingo, SUNDAY"
    })
    void diasDeLaSemana(String dia, DayOfWeek esperado) {
        Row row = rowWith(DataRow.defaultRow().toBuilder().day(dia).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.dayOfWeek()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("día inválido → InvalidRowException")
    void diaInvalido() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().day("Lunardi").build());

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Unknown day of week")
            .hasMessageContaining("Lunardi");
    }

    @Test
    @DisplayName("horas HHMM: 800 → 08:00 y 1830 → 18:30")
    void horasHHMM() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(800).endTime(1830).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    @DisplayName("celda requerida en blanco (Curso) → excepción que referencia columna y fila")
    void celdaRequeridaEnBlanco() {
        ExcelTestWorkbooks fixture = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withBlankCell(6, 0);
        Row row = fixture.dataRow(0);

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Curso")
            .hasMessageContaining("row 7");
    }

    @Test
    @DisplayName("tipo incorrecto en columna numérica (Comisión con texto) → excepción")
    void tipoIncorrecto() {
        ExcelTestWorkbooks fixture = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withStringCell(6, 1, "no-es-numero");
        Row row = fixture.dataRow(0);

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Comisión")
            .hasMessageContaining("must be numeric");
    }

    @Test
    @DisplayName("aula numérica se lee como string sin decimales: 105 → \"105\", no \"105.0\"")
    void aulaNumericaComoString() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().roomNumber(105).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.roomNumber()).isEqualTo("105");
    }

    @Test
    @DisplayName("aula alfanumérica se preserva como texto")
    void aulaAlfanumericaComoTexto() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().roomNumber("105B").build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.roomNumber()).isEqualTo("105B");
    }

    @Test
    @DisplayName("Durac[min] vacía → durationMinutes null (el fallback fin-inicio lo hace el service, no el mapper)")
    void duracionVaciaEsNull() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().durationMinutes(null).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.durationMinutes()).isNull();
    }

    @Test
    @DisplayName("Durac[min] presente → se propaga tal cual")
    void duracionPresenteSePropaga() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().durationMinutes(90).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.durationMinutes()).isEqualTo(90);
    }

    @ParameterizedTest(name = "Hora Comienzo {0} es inválida")
    @DisplayName("ING-02: Hora Comienzo fuera del rango HHMM (2575, -100, 2400, 2360) → InvalidRowException con columna y fila")
    @ValueSource(ints = {2575, -100, 2400, 2360, 60})
    void horaComienzoFueraDeRango(int hhmm) {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(hhmm).endTime(2359).build());

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Comienzo")
            .hasMessageContaining("HHMM")
            .hasMessageContaining("row 7");
    }

    @ParameterizedTest(name = "Hora Fin {0} es inválida")
    @DisplayName("ING-02: Hora Fin fuera del rango HHMM (2575, -100, 2400, 2360) → InvalidRowException con columna y fila")
    @ValueSource(ints = {2575, -100, 2400, 2360})
    void horaFinFueraDeRango(int hhmm) {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(1830).endTime(hhmm).build());

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Fin")
            .hasMessageContaining("HHMM")
            .hasMessageContaining("row 7");
    }

    @Test
    @DisplayName("ING-02: extremos válidos del día: 0 (00:00) como inicio y 2359 (23:59) como fin")
    void extremosValidosDelDia() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(0).endTime(2359).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.startTime()).isEqualTo(LocalTime.MIN);
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    @DisplayName("ING-02: Hora Fin un minuto posterior al inicio es el mínimo válido")
    void finUnMinutoDespuesDelInicioEsValido() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(1830).endTime(1831).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.endTime()).isEqualTo(LocalTime.of(18, 31));
    }

    @ParameterizedTest(name = "inicio {0} fin {1}")
    @DisplayName("ING-02: Hora Fin igual o anterior a Hora Comienzo → InvalidRowException")
    @CsvSource({"1830, 1830", "1830, 1829", "1830, 800"})
    void finIgualOAnteriorAlInicio(int inicio, int fin) {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(inicio).endTime(fin).build());

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Fin")
            .hasMessageContaining("after")
            .hasMessageContaining("row 7");
    }

    @Test
    @DisplayName("ING-02: celda de texto en Hora Comienzo → InvalidRowException que nombra la columna")
    void textoEnHoraComienzo() {
        Row row = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withStringCell(6, 6, "18:30")
            .dataRow(0);

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Comienzo")
            .hasMessageContaining("must be numeric")
            .hasMessageContaining("row 7");
    }

    @Test
    @DisplayName("ING-02: fórmula con resultado de texto en una columna numérica → InvalidRowException, no IllegalStateException de POI")
    void formulaConResultadoDeTexto() {
        Row row = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withFormulaCell(6, 6, "\"abc\"")
            .dataRow(0);

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Hora Comienzo")
            .hasMessageContaining("must be numeric");
    }

    @Test
    @DisplayName("fórmula con resultado numérico en una columna numérica se lee por su valor cacheado")
    void formulaConResultadoNumerico() {
        Row row = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withFormulaCell(6, 6, "1800+30")
            .dataRow(0);

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.startTime()).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    @DisplayName("ING-02: Durac[min] de texto → InvalidRowException que nombra la columna")
    void duracionDeTexto() {
        Row row = ExcelTestWorkbooks.validTemplate().withValidDataRow()
            .withStringCell(6, 9, "noventa")
            .dataRow(0);

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Durac[min]")
            .hasMessageContaining("must be numeric")
            .hasMessageContaining("row 7");
    }

    @ParameterizedTest(name = "Durac[min] {0} es inválida")
    @DisplayName("ING-02: Durac[min] cero o negativa → InvalidRowException")
    @ValueSource(ints = {0, -1, -90})
    void duracionNoPositiva(int minutos) {
        Row row = rowWith(DataRow.defaultRow().toBuilder().durationMinutes(minutos).build());

        assertThatThrownBy(() -> mapper.map(row, 7))
            .isInstanceOf(InvalidRowException.class)
            .hasMessageContaining("Durac[min]")
            .hasMessageContaining("greater than zero")
            .hasMessageContaining("row 7");
    }

    @Test
    @DisplayName("ING-02: Durac[min] = 1 es el mínimo válido")
    void duracionUnMinutoEsValida() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().durationMinutes(1).build());

        assertThat(mapper.map(row, 7).durationMinutes()).isEqualTo(1);
    }

    private Row rowWith(DataRow data) {
        return ExcelTestWorkbooks.validTemplate().withDataRow(data).dataRow(0);
    }
}
