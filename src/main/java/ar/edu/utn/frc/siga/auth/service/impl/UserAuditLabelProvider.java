package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.auth.model.User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a user: its email, read from the audited state. */
@Component
class UserAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return User.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String email = record.text("email");
            if (email != null) {
                labels.put(record.recordId(), email);
            }
        }
        return labels;
    }
}
