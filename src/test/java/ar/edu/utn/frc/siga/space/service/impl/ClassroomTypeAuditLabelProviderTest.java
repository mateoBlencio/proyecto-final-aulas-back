package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassroomTypeAuditLabelProvider")
class ClassroomTypeAuditLabelProviderTest {

    private final ClassroomTypeAuditLabelProvider provider = new ClassroomTypeAuditLabelProvider();

    private static AuditedRecord record(String id, String description) {
        Map<String, Object> values = new HashMap<>();
        values.put("description", description);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es ClassroomType")
    void entityType_isClassroomType() {
        assertThat(provider.entityType()).isEqualTo(ClassroomType.class);
    }

    @Test
    @DisplayName("la etiqueta es la descripción")
    void labels_exactFormat() {
        assertThat(provider.labels(List.of(record("1", "Laboratorio")))).containsExactly(Map.entry("1", "Laboratorio"));
    }

    @Test
    @DisplayName("varios registros se etiquetan cada uno con su propio estado")
    void labels_severalRecords_eachWithOwnState() {
        assertThat(provider.labels(List.of(record("1", "Laboratorio"), record("2", "Otro")))).hasSize(2)
                .containsEntry("1", "Laboratorio");
    }

    @Test
    @DisplayName("sin description en el estado el registro no tiene etiqueta")
    void labels_noValue_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null)))).isEmpty();
    }
}
