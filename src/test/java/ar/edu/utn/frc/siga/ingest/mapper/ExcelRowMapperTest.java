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
    @DisplayName("horas HHMM: 1830 → 18:30 y 800 → 08:00")
    void horasHHMM() {
        Row row = rowWith(DataRow.defaultRow().toBuilder().startTime(1830).endTime(800).build());

        RowDto dto = mapper.map(row, 7);

        assertThat(dto.startTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(8, 0));
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

    private Row rowWith(DataRow data) {
        return ExcelTestWorkbooks.validTemplate().withDataRow(data).dataRow(0);
    }
}
