package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectAuditLabelProvider")
class SubjectAuditLabelProviderTest {

    private final SubjectAuditLabelProvider provider = new SubjectAuditLabelProvider();

    private static AuditedRecord record(String id, String name, Integer code) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        values.put("code", code);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es Subject")
    void entityType_isSubject() {
        assertThat(provider.entityType()).isEqualTo(Subject.class);
    }

    @Test
    @DisplayName("formato '{nombre} ({código})'")
    void labels_nameAndCode() {
        assertThat(provider.labels(List.of(record("1", "Física I", 1))))
                .containsExactly(Map.entry("1", "Física I (1)"));
    }

    @Test
    @DisplayName("sin código muestra solo el nombre")
    void labels_noCode_showsOnlyName() {
        assertThat(provider.labels(List.of(record("1", "Física I", null))))
                .containsExactly(Map.entry("1", "Física I"));
    }

    @Test
    @DisplayName("sin nombre el registro no tiene etiqueta aunque tenga código")
    void labels_noName_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null, 1)))).isEmpty();
    }
}
