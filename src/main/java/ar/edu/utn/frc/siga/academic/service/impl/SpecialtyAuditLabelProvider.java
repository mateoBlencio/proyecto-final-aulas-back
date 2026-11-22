package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a specialty: its name. */
@Component
class SpecialtyAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return Specialty.class;
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
