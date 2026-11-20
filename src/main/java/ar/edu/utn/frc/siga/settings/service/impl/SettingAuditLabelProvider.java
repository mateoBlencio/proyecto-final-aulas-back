package ar.edu.utn.frc.siga.settings.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Label of a setting: the Spanish name of its key ("Aula demasiado grande"). Keys no longer in the catalog get none. */
@Component
class SettingAuditLabelProvider implements AuditLabelProvider {

    private static final Map<String, String> LABELS = Arrays.stream(SettingKey.values())
            .collect(Collectors.toMap(SettingKey::getKey, SettingKey::getLabel));

    @Override
    public Class<?> entityType() {
        return Setting.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String label = LABELS.get(record.recordId());
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }
}
