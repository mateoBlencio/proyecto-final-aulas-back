package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpecialtyAuditLabelProvider")
class SpecialtyAuditLabelProviderTest {

    private final SpecialtyAuditLabelProvider provider = new SpecialtyAuditLabelProvider();

    private static AuditedRecord record(String id, String name) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es Specialty")
    void entityType_isSpecialty() {
        assertThat(provider.entityType()).isEqualTo(Specialty.class);
    }

    @Test
    @DisplayName("la etiqueta es el nombre")
    void labels_exactFormat() {
        assertThat(provider.labels(List.of(record("1", "Ingeniería en Sistemas")))).containsExactly(Map.entry("1", "Ingeniería en Sistemas"));
    }

    @Test
    @DisplayName("varios registros se etiquetan cada uno con su propio estado")
    void labels_severalRecords_eachWithOwnState() {
        assertThat(provider.labels(List.of(record("1", "Ingeniería en Sistemas"), record("2", "Otro")))).hasSize(2)
                .containsEntry("1", "Ingeniería en Sistemas");
    }

    @Test
    @DisplayName("sin name en el estado el registro no tiene etiqueta")
    void labels_noValue_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null)))).isEmpty();
    }
}
