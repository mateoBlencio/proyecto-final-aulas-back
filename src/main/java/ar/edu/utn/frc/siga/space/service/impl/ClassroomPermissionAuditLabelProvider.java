package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
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
 * Label of a classroom permission: "Materia Física I en Aula 101, Central". Deactivated subjects and classrooms
 * are included. If one of the two references is missing, the label keeps the other part.
 */
@Component
@RequiredArgsConstructor
class ClassroomPermissionAuditLabelProvider implements AuditLabelProvider {

    private final SubjectService subjectService;
    private final ClassroomService classroomService;

    @Override
    public Class<?> entityType() {
        return ClassroomPermission.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> subjectIds = records.stream().filter(ClassroomPermissionAuditLabelProvider::targetsSubject)
                .map(record -> record.longValue("targetId")).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> classroomIds = records.stream().map(record -> record.longValue("classroom"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SubjectResponseDto> subjects = subjectIds.isEmpty() ? new HashMap<>()
                : Maps.byId(subjectService.findByIdsIncludingDeactivated(subjectIds), SubjectResponseDto::id);
        Map<Long, ClassroomResponseDto> classrooms = classroomIds.isEmpty() ? new HashMap<>()
                : Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            SubjectResponseDto subject = targetsSubject(record) ? subjects.get(record.longValue("targetId")) : null;
            ClassroomResponseDto classroom = classrooms.get(record.longValue("classroom"));
            String target = subject == null ? null : "Materia " + subject.name();
            String room = classroom == null ? null : "Aula " + classroom.roomNumber() + ", " + classroom.buildingName();
            String label = target == null ? room : room == null ? target : target + " en " + room;
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }

    private static boolean targetsSubject(AuditedRecord record) {
        return PermissionTargetKind.SUBJECT.name().equals(record.text("targetKind"));
    }
}
