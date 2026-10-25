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
            labels.put(record.recordId(), buildingName == null ? "Aula " + number : "Aula " + number + ", " + buildingName);
        }
        return labels;
    }
}
