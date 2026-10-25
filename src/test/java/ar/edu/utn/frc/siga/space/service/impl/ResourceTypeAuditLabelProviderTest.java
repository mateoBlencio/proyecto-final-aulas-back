package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResourceTypeAuditLabelProvider")
class ResourceTypeAuditLabelProviderTest {

    private final ResourceTypeAuditLabelProvider provider = new ResourceTypeAuditLabelProvider();

    private static AuditedRecord record(String id, String name) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es ResourceType")
    void entityType_isResourceType() {
        assertThat(provider.entityType()).isEqualTo(ResourceType.class);
    }

    @Test
    @DisplayName("la etiqueta es el nombre")
    void labels_exactFormat() {
        assertThat(provider.labels(List.of(record("1", "Proyector")))).containsExactly(Map.entry("1", "Proyector"));
    }

    @Test
    @DisplayName("varios registros se etiquetan cada uno con su propio estado")
    void labels_severalRecords_eachWithOwnState() {
        assertThat(provider.labels(List.of(record("1", "Proyector"), record("2", "Otro")))).hasSize(2)
                .containsEntry("1", "Proyector");
    }

    @Test
    @DisplayName("sin name en el estado el registro no tiene etiqueta")
    void labels_noValue_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null)))).isEmpty();
    }
}
