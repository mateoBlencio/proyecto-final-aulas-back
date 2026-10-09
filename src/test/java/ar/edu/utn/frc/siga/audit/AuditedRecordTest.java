package ar.edu.utn.frc.siga.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditedRecord")
class AuditedRecordTest {

    private static AuditedRecord recordWith(String property, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(property, value);
        return new AuditedRecord("1", values);
    }

    @Test
    @DisplayName("longValue convierte cualquier Number")
    void longValue_number() {
        assertThat(recordWith("p", 5).longValue("p")).isEqualTo(5L);
        assertThat(recordWith("p", 5L).longValue("p")).isEqualTo(5L);
    }

    @Test
    @DisplayName("longValue acepta un String numérico, con espacios alrededor")
    void longValue_numericString() {
        assertThat(recordWith("p", "42").longValue("p")).isEqualTo(42L);
        assertThat(recordWith("p", " 42 ").longValue("p")).isEqualTo(42L);
    }

    @Test
    @DisplayName("longValue devuelve null para texto no numérico, valor nulo, propiedad ausente u otro tipo")
    void longValue_unparseableOrAbsent() {
        assertThat(recordWith("p", "abc").longValue("p")).isNull();
        assertThat(recordWith("p", "").longValue("p")).isNull();
        assertThat(recordWith("p", null).longValue("p")).isNull();
        assertThat(recordWith("p", true).longValue("p")).isNull();
        assertThat(new AuditedRecord("1", Map.of()).longValue("p")).isNull();
    }

    @Test
    @DisplayName("parseLong devuelve null si el texto es null o no numérico")
    void parseLong_invalid() {
        assertThat(AuditedRecord.parseLong("7")).isEqualTo(7L);
        assertThat(AuditedRecord.parseLong(null)).isNull();
        assertThat(AuditedRecord.parseLong("7.5")).isNull();
        assertThat(AuditedRecord.parseLong("99999999999999999999")).isNull();
    }

    @Test
    @DisplayName("text devuelve toString del valor o null")
    void text() {
        assertThat(recordWith("p", 12).text("p")).isEqualTo("12");
        assertThat(recordWith("p", null).text("p")).isNull();
        assertThat(new AuditedRecord("1", Map.of()).text("p")).isNull();
    }
}
