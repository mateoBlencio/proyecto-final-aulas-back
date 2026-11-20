package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicLabels;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditLabels;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Label of a room request item: "Análisis Matemático I (1K1) · 04/03/2026", or "· Lunes" when the item is tied to a
 * teaching day. The summary is only the subject and commission. Subjects and commissions of deactivated records are
 * included.
 */
@Component
@RequiredArgsConstructor
class RoomRequestItemAuditLabelProvider implements AuditLabelProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RoomRequestRepository requestRepository;
    private final SubjectService subjectService;
    private final CommissionService commissionService;

    @Override
    public Class<?> entityType() {
        return RoomRequestItem.class;
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
        Set<Long> requestIds = ids(records, "request");
        Map<Long, Long> subjectIdByRequest = new HashMap<>();
        if (!requestIds.isEmpty()) {
            for (RoomRequest request : requestRepository.findAllById(requestIds)) {
                subjectIdByRequest.put(request.getId(), request.getSubjectId());
            }
        }
        Set<Long> subjectIds = subjectIdByRequest.values().stream().filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> commissionIds = ids(records, "commissionId");
        Map<Long, SubjectResponseDto> subjects = subjectIds.isEmpty() ? new HashMap<>()
                : Maps.byId(subjectService.findByIdsIncludingDeactivated(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissions = commissionIds.isEmpty() ? new HashMap<>()
                : Maps.byId(commissionService.findByIdsIncludingDeactivated(commissionIds), CommissionResponseDto::id);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            SubjectResponseDto subject = subjects.get(subjectIdByRequest.get(record.longValue("request")));
            CommissionResponseDto commission = commissions.get(record.longValue("commissionId"));
            String identity = AcademicLabels.subjectCommission(subject == null ? null : subject.name(),
                    commission == null ? null : commission.courseCode());
            String when = summary ? null : when(record);
            String label = identity == null ? when : when == null ? identity : identity + " · " + when;
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }

    private static String when(AuditedRecord record) {
        if (record.values().get("date") instanceof LocalDate date) {
            return date.format(DATE);
        }
        String dayOfWeek = record.text("dayOfWeek");
        return dayOfWeek == null ? null : AuditLabels.constant(DayOfWeek.valueOf(dayOfWeek));
    }

    private static Set<Long> ids(List<AuditedRecord> records, String property) {
        return records.stream().map(record -> record.longValue(property)).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
