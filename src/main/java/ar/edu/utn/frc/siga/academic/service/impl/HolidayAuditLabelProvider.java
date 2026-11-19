package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Holiday;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a holiday: "Feriado 09/07/2026 · Día de la Independencia". The record id is the ISO date. */
@Component
class HolidayAuditLabelProvider implements AuditLabelProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public Class<?> entityType() {
        return Holiday.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        return build(records, false);
    }

    @Override
    public Map<String, String> summaryLabels(List<AuditedRecord> records) {
        return build(records, true);
    }

    private Map<String, String> build(List<AuditedRecord> records, boolean summary) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String date = formatted(record.recordId());
            if (date == null) {
                continue;
            }
            String description = record.text("description");
            boolean hasDescription = description != null && !description.isBlank();
            labels.put(record.recordId(), !hasDescription ? (summary ? date : "Feriado " + date)
                    : summary ? date + " (" + description + ")" : "Feriado " + date + " · " + description);
        }
        return labels;
    }

    private static String formatted(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
