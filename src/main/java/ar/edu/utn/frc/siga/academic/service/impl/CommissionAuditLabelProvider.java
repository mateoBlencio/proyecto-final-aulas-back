package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Label of a commission: "Comisión 5D1, 2026/1". Without the period, only the course code. */
@Component
@RequiredArgsConstructor
class CommissionAuditLabelProvider implements AuditLabelProvider {

    private final AcademicPeriodRepository academicPeriodRepository;

    @Override
    public Class<?> entityType() {
        return Commission.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> periodIds = records.stream().map(record -> record.longValue("academicPeriod"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AcademicPeriod> periods = periodIds.isEmpty() ? new HashMap<>()
                : academicPeriodRepository.findAllById(periodIds).stream()
                        .collect(Collectors.toMap(AcademicPeriod::getId, period -> period));

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String code = record.text("courseCode");
            if (code == null) {
                continue;
            }
            AcademicPeriod period = periods.get(record.longValue("academicPeriod"));
            labels.put(record.recordId(), period == null ? "Comisión " + code
                    : "Comisión " + code + ", " + period.getYear() + "/" + period.getSemester());
        }
        return labels;
    }
}
