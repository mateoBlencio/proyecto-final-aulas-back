package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
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

/** Label of a study plan: "Plan 2023, Ingeniería en Sistemas". Without the specialty, only the plan code. */
@Component
@RequiredArgsConstructor
class StudyPlanAuditLabelProvider implements AuditLabelProvider {

    private final SpecialtyRepository specialtyRepository;

    @Override
    public Class<?> entityType() {
        return StudyPlan.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> specialtyIds = records.stream().map(record -> record.longValue("specialty"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = specialtyIds.isEmpty() ? new HashMap<>()
                : specialtyRepository.findAllById(specialtyIds).stream()
                        .filter(specialty -> specialty.getName() != null)
                        .collect(Collectors.toMap(Specialty::getId, Specialty::getName));

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String code = record.text("planCode");
            if (code == null) {
                continue;
            }
            String specialty = names.get(record.longValue("specialty"));
            labels.put(record.recordId(), specialty == null ? "Plan " + code : "Plan " + code + ", " + specialty);
        }
        return labels;
    }
}
