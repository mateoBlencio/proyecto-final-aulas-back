package ar.edu.utn.frc.siga.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FieldValueFormatter")
class FieldValueFormatterTest {

    private final FieldValueFormatter formatter = FieldValueFormatter.withoutReferences();

    @Test
    @DisplayName("una columna Instant guardada en UTC se muestra en hora de Argentina")
    void instantColumnIsShownInLocalTime() {
        AuditedColumn column = new AuditedColumn("t_aud", "eliminado_en", "deletedAt", Instant.class, null);

        assertThat(formatter.format(column, LocalDateTime.of(2026, 11, 1, 6, 0))).isEqualTo("01/11/2026 03:00");
        assertThat(formatter.label(column)).isEqualTo("Fecha de baja");
    }

    @Test
    @DisplayName("una columna LocalDateTime se muestra tal cual")
    void localDateTimeColumnIsNotShifted() {
        AuditedColumn column = new AuditedColumn("t_aud", "fecha", "date", LocalDateTime.class, null);

        assertThat(formatter.format(column, LocalDateTime.of(2026, 11, 1, 6, 0))).isEqualTo("01/11/2026 06:00");
    }
}
