package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomListItemDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomPermissionTargetDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResourceDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.permission.PermissionTargetResolvers;
import ar.edu.utn.frc.siga.space.permission.TargetInfo;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassroomListComposer {

    private static final String DISPLAY_ALL = "Todas";
    private static final String DISPLAY_NONE = "Ninguna";
    private static final String DISPLAY_SOME = "Algunos";

    private final ClassroomResourceRepository classroomResourceRepository;
    private final ClassroomPermissionRepository classroomPermissionRepository;
    private final PermissionTargetResolvers permissionTargetResolvers;
    private final SpaceActionFlags actionFlags;

    public ClassroomListItemDto compose(Classroom classroom) {
        return compose(new PageImpl<>(List.of(classroom))).getContent().getFirst();
    }

    public Page<ClassroomListItemDto> compose(Page<Classroom> classrooms) {
        List<Long> classroomIds = classrooms.getContent().stream().map(Classroom::getId).toList();

        Map<Long, List<ClassroomResource>> resourcesByClassroom = classroomIds.isEmpty()
                ? Map.of()
                : classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(classroomIds).stream()
                        .filter(resource -> resource.getResourceType().isActive())
                        .collect(Collectors.groupingBy(resource -> resource.getClassroom().getId()));

        Map<Long, List<ClassroomPermission>> permissionsByClassroom = classroomIds.isEmpty()
                ? Map.of()
                : classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(classroomIds).stream()
                        .collect(Collectors.groupingBy(permission -> permission.getClassroom().getId()));

        Map<PermissionTargetKind, Map<Long, TargetInfo>> namesByKind =
                resolveTargetNames(permissionsByClassroom.values());

        BuildingScope updateScope = actionFlags.userScope(Permission.CLASSROOM_UPDATE);
        BuildingScope activateScope = actionFlags.userScope(Permission.CLASSROOM_ACTIVATE);
        BuildingScope allocateScope = actionFlags.userScope(Permission.ALLOCATION_WRITE);

        return classrooms.map(classroom -> toDto(
                classroom,
                resourcesByClassroom.getOrDefault(classroom.getId(), List.of()),
                permissionsByClassroom.getOrDefault(classroom.getId(), List.of()),
                namesByKind,
                updateScope, activateScope, allocateScope));
    }

    private Map<PermissionTargetKind, Map<Long, TargetInfo>> resolveTargetNames(
            Collection<List<ClassroomPermission>> permissionLists) {
        Map<PermissionTargetKind, Set<Long>> idsByKind = permissionLists.stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(ClassroomPermission::getTargetKind,
                        Collectors.mapping(ClassroomPermission::getTargetId, Collectors.toCollection(LinkedHashSet::new))));

        return idsByKind.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> permissionTargetResolvers.resolve(entry.getKey(), entry.getValue())));
    }

    private ClassroomListItemDto toDto(Classroom classroom,
                                       List<ClassroomResource> resources,
                                       List<ClassroomPermission> permissions,
                                       Map<PermissionTargetKind, Map<Long, TargetInfo>> namesByKind,
                                       BuildingScope updateScope,
                                       BuildingScope activateScope,
                                       BuildingScope allocateScope) {
        List<ClassroomResourceDto> resourceDtos = resources.stream()
                .map(resource -> new ClassroomResourceDto(
                        resource.getResourceType().getId(),
                        resource.getResourceType().getName(),
                        resource.getResourceType().getValueKind(),
                        resource.getQuantity()))
                .toList();

        List<ClassroomPermissionTargetDto> targetDtos = permissions.stream()
                .map(permission -> toTargetDto(permission, namesByKind))
                .toList();

        return new ClassroomListItemDto(
                classroom.getId(),
                classroom.getRoomNumber(),
                classroom.getBuilding().getId(),
                classroom.getBuilding().getName(),
                classroom.getClassroomType().getId(),
                classroom.getClassroomType().getDescription(),
                classroom.getCapacity(),
                resourceDtos,
                classroom.getObservations(),
                classroom.isActive(),
                classroom.getPermissionMode(),
                allowedDisplay(classroom.getPermissionMode(), targetDtos),
                targetDtos,
                classroom.unavailableReason(),
                classroom.isCurrentInSysacad(),
                classroom.isActive() && updateScope.allows(classroom.getBuilding().getId()),
                activateScope.allows(classroom.getBuilding().getId()),
                classroom.isAvailable() && allocateScope.allows(classroom.getBuilding().getId()));
    }

    private ClassroomPermissionTargetDto toTargetDto(
            ClassroomPermission permission, Map<PermissionTargetKind, Map<Long, TargetInfo>> infoByKind) {
        TargetInfo info = infoByKind.getOrDefault(permission.getTargetKind(), Map.of()).get(permission.getTargetId());
        if (info == null) {
            return new ClassroomPermissionTargetDto(
                    permission.getTargetKind(), permission.getTargetId(), null, null, null, null, null);
        }
        return new ClassroomPermissionTargetDto(
                permission.getTargetKind(), permission.getTargetId(), info.name(),
                info.specialtyName(), info.specialtyAbbreviation(), info.specialtyCode(), info.studyPlanCode());
    }

    private String allowedDisplay(PermissionMode mode, List<ClassroomPermissionTargetDto> targets) {
        return switch (mode) {
            case ALL -> DISPLAY_ALL;
            case NONE -> DISPLAY_NONE;
            case SUBSET -> switch (targets.size()) {
                case 0 -> DISPLAY_NONE;
                case 1 -> targets.getFirst().name() != null ? targets.getFirst().name() : DISPLAY_SOME;
                default -> DISPLAY_SOME;
            };
        };
    }
}
