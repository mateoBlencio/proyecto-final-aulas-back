package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Label of a classroom: "Aula 101, Central". Deactivated buildings are included. Without the building, only
 * the room number.
 */
@Component
@RequiredArgsConstructor
class ClassroomAuditLabelProvider implements AuditLabelProvider {

    private final BuildingRepository buildingRepository;

    @Override
    public Class<?> entityType() {
        return Classroom.class;
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
        Set<Long> buildingIds = records.stream().map(record -> record.longValue("building"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> buildingNames = buildingIds.isEmpty() ? new HashMap<>()
                : buildingRepository.findAllById(buildingIds).stream()
                        .collect(Collectors.toMap(Building::getId, Building::getName));

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String number = record.text("roomNumber");
            if (number == null) {
                continue;
            }
            String buildingName = buildingNames.get(record.longValue("building"));
            String room = "Aula " + number;
            labels.put(record.recordId(), buildingName == null ? room
                    : summary ? room + " (" + buildingName + ")" : room + ", " + buildingName);
        }
        return labels;
    }
}
