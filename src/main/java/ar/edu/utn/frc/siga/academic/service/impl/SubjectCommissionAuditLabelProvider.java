package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Label of a subject-commission link: "Análisis Matemático I, comisión 1K1". */
@Component
@RequiredArgsConstructor
class SubjectCommissionAuditLabelProvider implements AuditLabelProvider {

    private final SubjectRepository subjectRepository;
    private final CommissionRepository commissionRepository;

    @Override
    public Class<?> entityType() {
        return SubjectCommission.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> subjectIds = idsOf(records, "subject");
        Set<Long> commissionIds = idsOf(records, "commission");
        Map<Long, String> subjectNames = subjectIds.isEmpty() ? new HashMap<>()
                : subjectRepository.findAllById(subjectIds).stream()
                        .collect(Collectors.toMap(Subject::getId, Subject::getName));
        Map<Long, String> courseCodes = commissionIds.isEmpty() ? new HashMap<>()
                : commissionRepository.findAllById(commissionIds).stream()
                        .collect(Collectors.toMap(Commission::getId, Commission::getCourseCode));

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String subject = subjectNames.get(record.longValue("subject"));
            String commission = courseCodes.get(record.longValue("commission"));
            if (subject != null && commission != null) {
                labels.put(record.recordId(), subject + ", comisión " + commission);
            }
        }
        return labels;
    }

    private static Set<Long> idsOf(List<AuditedRecord> records, String property) {
        return records.stream().map(record -> record.longValue(property)).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
