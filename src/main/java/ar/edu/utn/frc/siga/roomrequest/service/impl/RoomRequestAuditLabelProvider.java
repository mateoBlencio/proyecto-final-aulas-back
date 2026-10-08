package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Label of a room request: "Solicitud de Juan Pérez", read from the audited state. */
@Component
class RoomRequestAuditLabelProvider implements AuditLabelProvider {

    @Override
    public Class<?> entityType() {
        return RoomRequest.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String teacher = record.text("teacherName");
            if (teacher != null) {
                labels.put(record.recordId(), "Solicitud de " + teacher);
            }
        }
        return labels;
    }
}
