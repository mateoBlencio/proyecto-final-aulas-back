package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a subject: "Física I (1)", name and code. Without the code, only the name. */
@Component
class SubjectAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return Subject.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String name = record.text("name");
            if (name == null) {
                continue;
            }
            String code = record.text("code");
            labels.put(record.recordId(), code == null ? name : name + " (" + code + ")");
        }
        return labels;
    }
}
