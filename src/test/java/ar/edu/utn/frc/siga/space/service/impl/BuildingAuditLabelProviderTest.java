package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.Building;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BuildingAuditLabelProvider")
class BuildingAuditLabelProviderTest {

    private final BuildingAuditLabelProvider provider = new BuildingAuditLabelProvider();

    private static AuditedRecord record(String id, String name) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es Building")
    void entityType_isBuilding() {
        assertThat(provider.entityType()).isEqualTo(Building.class);
    }

    @Test
    @DisplayName("formato 'Edificio {nombre}'")
    void labels_exactFormat() {
        assertThat(provider.labels(List.of(record("1", "Central")))).containsExactly(Map.entry("1", "Edificio Central"));
    }

    @Test
    @DisplayName("varios registros se etiquetan cada uno con su propio estado")
    void labels_severalRecords_eachWithOwnState() {
        assertThat(provider.labels(List.of(record("1", "Central"), record("2", "Otro")))).hasSize(2)
                .containsEntry("1", "Edificio Central");
    }

    @Test
    @DisplayName("sin name en el estado el registro no tiene etiqueta")
    void labels_noValue_hasNoLabel() {
        assertThat(provider.labels(List.of(record("1", null)))).isEmpty();
    }
}
