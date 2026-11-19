package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Label of a classroom preference: "Aula 101, Central" ("Aula 101 (Central)" in the summary). Deactivated classrooms are included. */
@Component
@RequiredArgsConstructor
class RoomPreferenceAuditLabelProvider implements AuditLabelProvider {

    private final ClassroomService classroomService;

    @Override
    public Class<?> entityType() {
        return RoomPreference.class;
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
        Set<Long> classroomIds = records.stream().map(record -> record.longValue("classroomId"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ClassroomResponseDto> classrooms = classroomIds.isEmpty() ? new HashMap<>()
                : Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            ClassroomResponseDto classroom = classrooms.get(record.longValue("classroomId"));
            if (classroom != null) {
                String room = "Aula " + classroom.roomNumber();
                labels.put(record.recordId(), summary ? room + " (" + classroom.buildingName() + ")"
                        : room + ", " + classroom.buildingName());
            }
        }
        return labels;
    }
}
