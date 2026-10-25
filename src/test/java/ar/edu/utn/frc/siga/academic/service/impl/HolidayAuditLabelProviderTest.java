package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Holiday;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HolidayAuditLabelProvider")
class HolidayAuditLabelProviderTest {

    private final HolidayAuditLabelProvider provider = new HolidayAuditLabelProvider();

    private static AuditedRecord record(String id, String description) {
        Map<String, Object> values = new HashMap<>();
        values.put("description", description);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es Holiday")
    void entityType_isHoliday() {
        assertThat(provider.entityType()).isEqualTo(Holiday.class);
    }

    @Test
    @DisplayName("formato 'Feriado {dd/MM/yyyy} · {descripción}'")
    void labels_dateAndDescription() {
        assertThat(provider.labels(List.of(record("2026-07-09", "Día de la Independencia"))))
                .containsExactly(Map.entry("2026-07-09", "Feriado 09/07/2026 · Día de la Independencia"));
    }

    @Test
    @DisplayName("sin descripción o con descripción en blanco muestra solo la fecha")
    void labels_noDescription_showsOnlyDate() {
        assertThat(provider.labels(List.of(record("2026-07-09", null), record("2026-05-25", "  "))))
                .containsOnly(Map.entry("2026-07-09", "Feriado 09/07/2026"),
                        Map.entry("2026-05-25", "Feriado 25/05/2026"));
    }

    @Test
    @DisplayName("un id que no es fecha ISO deja el registro sin etiqueta")
    void labels_invalidDateId_hasNoLabel() {
        assertThat(provider.labels(List.of(record("09/07/2026", "Día de la Independencia")))).isEmpty();
    }
}
