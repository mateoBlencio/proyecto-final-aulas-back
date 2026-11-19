package ar.edu.utn.frc.siga.settings.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettingAuditLabelProvider")
class SettingAuditLabelProviderTest {

    private final SettingAuditLabelProvider provider = new SettingAuditLabelProvider();

    private static AuditedRecord record(String key) {
        return new AuditedRecord(key, Map.of("value", "30"));
    }

    @Test
    @DisplayName("entityType es Setting")
    void entityType_isSetting() {
        assertThat(provider.entityType()).isEqualTo(Setting.class);
    }

    @Test
    @DisplayName("la etiqueta es el nombre en español de la clave, no la clave técnica")
    void labels_usesSpanishNameOfKey() {
        assertThat(provider.labels(List.of(record("allocation.maxOverlapMinutes"), record("preview.ttlMinutes"))))
                .containsOnly(Map.entry("allocation.maxOverlapMinutes", "Minutos de solapamiento"),
                        Map.entry("preview.ttlMinutes", "Vista previa: vigencia"));
    }

    @ParameterizedTest
    @EnumSource(SettingKey.class)
    @DisplayName("toda clave del catálogo tiene etiqueta y es el label del enum")
    void labels_everyCatalogKey_hasLabel(SettingKey key) {
        assertThat(provider.labels(List.of(record(key.getKey()))))
                .containsExactly(Map.entry(key.getKey(), key.getLabel()));
    }

    @Test
    @DisplayName("una clave que ya no está en el catálogo no tiene etiqueta y no rompe el lote")
    void labels_unknownKey_hasNoLabel() {
        assertThat(provider.labels(List.of(record("clave.vieja"), record("preview.ttlMinutes"))))
                .containsExactly(Map.entry("preview.ttlMinutes", "Vista previa: vigencia"));
    }
}
