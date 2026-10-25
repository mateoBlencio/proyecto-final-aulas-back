package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a classroom type: its description. */
@Component
class ClassroomTypeAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return ClassroomType.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String description = record.text("description");
            if (description != null) {
                labels.put(record.recordId(), description);
            }
        }
        return labels;
    }
}
