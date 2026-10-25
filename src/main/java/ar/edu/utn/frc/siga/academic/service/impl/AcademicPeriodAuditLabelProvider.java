package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of an academic period: "2026/1", year and semester. */
@Component
class AcademicPeriodAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return AcademicPeriod.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String year = record.text("year");
            String semester = record.text("semester");
            if (year != null && semester != null) {
                labels.put(record.recordId(), year + "/" + semester);
            }
        }
        return labels;
    }
}
