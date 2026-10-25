package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Label of a classroom resource: "Proyector en Aula 101, Central". Deactivated classrooms and resource types
 * are included. If one of the two references is missing, the label keeps the other part.
 */
@Component
@RequiredArgsConstructor
class ClassroomResourceAuditLabelProvider implements AuditLabelProvider {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ClassroomService classroomService;

    @Override
    public Class<?> entityType() {
        return ClassroomResource.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> typeIds = records.stream().map(record -> record.longValue("resourceType"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> classroomIds = records.stream().map(record -> record.longValue("classroom"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> typeNames = typeIds.isEmpty() ? new HashMap<>()
                : resourceTypeRepository.findAllById(typeIds).stream()
                        .collect(Collectors.toMap(ResourceType::getId, ResourceType::getName));
        Map<Long, ClassroomResponseDto> classrooms = classroomIds.isEmpty() ? new HashMap<>()
                : Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String type = typeNames.get(record.longValue("resourceType"));
            ClassroomResponseDto classroom = classrooms.get(record.longValue("classroom"));
            String room = classroom == null ? null : "Aula " + classroom.roomNumber() + ", " + classroom.buildingName();
            String label = type == null ? room : room == null ? type : type + " en " + room;
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }
}
