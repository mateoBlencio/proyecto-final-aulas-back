package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.Building;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a building: "Edificio Central". Read from the audited state, so deleted records are labelled too. */
@Component
class BuildingAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return Building.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        return build(records, "Edificio ");
    }

    @Override
    public Map<String, String> summaryLabels(List<AuditedRecord> records) {
        return build(records, "");
    }

    private static Map<String, String> build(List<AuditedRecord> records, String prefix) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String name = record.text("name");
            if (name != null) {
                labels.put(record.recordId(), prefix + name);
            }
        }
        return labels;
    }
}
