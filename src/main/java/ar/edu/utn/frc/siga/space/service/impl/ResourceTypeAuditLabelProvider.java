package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a resource type: its name. */
@Component
class ResourceTypeAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return ResourceType.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String name = record.text("name");
            if (name != null) {
                labels.put(record.recordId(), name);
            }
        }
        return labels;
    }
}
