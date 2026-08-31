package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomPermissionTargetRequestDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomResourceRequestDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassroomFeatureWriter {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ClassroomResourceRepository classroomResourceRepository;
    private final ClassroomPermissionRepository classroomPermissionRepository;

    public void applyResources(Classroom classroom, List<ClassroomResourceRequestDto> requested) {
        List<ClassroomResourceRequestDto> desired = requested != null ? requested : List.of();

        Map<Long, ClassroomResource> existing = new HashMap<>();
        for (ClassroomResource resource : classroomResourceRepository.findByClassroomId(classroom.getId())) {
            existing.put(resource.getResourceType().getId(), resource);
        }

        Set<Long> keep = new HashSet<>();
        for (ClassroomResourceRequestDto request : desired) {
            keep.add(request.resourceTypeId());
            ClassroomResource resource = existing.get(request.resourceTypeId());
            if (resource == null) {
                classroomResourceRepository.save(ClassroomResource.builder()
                        .classroom(classroom)
                        .resourceType(resolveResourceType(request.resourceTypeId()))
                        .quantity(request.quantity())
                        .build());
            } else {
                resource.setQuantity(request.quantity());
                if (resource.isDeleted()) {
                    resource.activate();
                }
                classroomResourceRepository.save(resource);
            }
        }

        existing.forEach((resourceTypeId, resource) -> {
            if (!keep.contains(resourceTypeId) && resource.isActive()) {
                classroomResourceRepository.softDelete(resource);
            }
        });
    }

    public void applyPermissions(Classroom classroom, PermissionMode mode,
                                 List<ClassroomPermissionTargetRequestDto> requested) {
        List<ClassroomPermissionTargetRequestDto> desired =
                mode == PermissionMode.SUBSET && requested != null ? requested : List.of();

        Map<String, ClassroomPermission> existing = new HashMap<>();
        for (ClassroomPermission permission : classroomPermissionRepository.findByClassroomId(classroom.getId())) {
            existing.put(key(permission.getTargetKind().name(), permission.getTargetId()), permission);
        }

        Set<String> keep = new HashSet<>();
        for (ClassroomPermissionTargetRequestDto request : desired) {
            String key = key(request.targetKind().name(), request.targetId());
            keep.add(key);
            ClassroomPermission permission = existing.get(key);
            if (permission == null) {
                classroomPermissionRepository.save(ClassroomPermission.builder()
                        .classroom(classroom)
                        .targetKind(request.targetKind())
                        .targetId(request.targetId())
                        .build());
            } else if (permission.isDeleted()) {
                classroomPermissionRepository.restore(permission);
            }
        }

        existing.forEach((key, permission) -> {
            if (!keep.contains(key) && permission.isActive()) {
                classroomPermissionRepository.softDelete(permission);
            }
        });
    }

    private ResourceType resolveResourceType(Long resourceTypeId) {
        return resourceTypeRepository.findActiveById(resourceTypeId)
                .orElseThrow(() -> new SpaceDomainException("Unknown resource type id: " + resourceTypeId));
    }

    private static String key(String kind, Long targetId) {
        return kind + "#" + targetId;
    }
}
