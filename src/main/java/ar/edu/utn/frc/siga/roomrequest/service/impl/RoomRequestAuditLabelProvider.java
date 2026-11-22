package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Label of a room request: "Solicitud de Juan Pérez", read from the audited state. The summary is
 * "Juan Pérez (Análisis Matemático I)"; the subject is omitted when the request has none.
 */
@Component
@RequiredArgsConstructor
class RoomRequestAuditLabelProvider implements AuditLabelProvider {

    private final SubjectService subjectService;

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

    @Override
    public Map<String, String> summaryLabels(List<AuditedRecord> records) {
        Set<Long> subjectIds = records.stream().map(record -> record.longValue("subjectId"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SubjectResponseDto> subjects = subjectIds.isEmpty() ? new HashMap<>()
                : Maps.byId(subjectService.findByIdsIncludingDeactivated(subjectIds), SubjectResponseDto::id);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String teacher = record.text("teacherName");
            if (teacher == null) {
                continue;
            }
            SubjectResponseDto subject = subjects.get(record.longValue("subjectId"));
            labels.put(record.recordId(), subject == null ? teacher : teacher + " (" + subject.name() + ")");
        }
        return labels;
    }
}
