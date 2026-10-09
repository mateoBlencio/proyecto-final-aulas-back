package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of an event: "Álgebra 1K1"; a unique event without subject shows its description. */
@Component
@RequiredArgsConstructor
class AcademicEventAuditLabelProvider implements AuditLabelProvider {

    private final EventLabels eventLabels;

    @Override
    public Class<?> entityType() {
        return AcademicEvent.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<Long, EventLabels.EventRef> refs = new HashMap<>();
        for (AuditedRecord record : records) {
            Long eventId = AuditedRecord.parseLong(record.recordId());
            if (eventId == null) {
                continue;
            }
            refs.put(eventId, new EventLabels.EventRef(
                    record.longValue("subjectId"), record.longValue("commissionId"), record.text("description")));
        }
        Map<String, String> labels = new HashMap<>();
        eventLabels.labelsOf(refs).forEach((eventId, label) -> labels.put(eventId.toString(), label));
        return labels;
    }
}
