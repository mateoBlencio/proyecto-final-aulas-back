package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import ar.edu.utn.frc.siga.common.security.BuildingScopedSpecifications;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Único acceso a {@link ClassroomRepository} con el alcance por edificio como parámetro obligatorio:
 * no se puede leer un aula sin decir contra qué {@link Permission} se valida su edificio. Fuera de
 * alcance devuelve 404 (no revela que el recurso existe), igual que el fetch que el service iba a
 * hacer igual. Ver .claude/docs/devolucion-rbac.md §2.
 */
@Component
@RequiredArgsConstructor
class ScopedClassroomFinder {

    private final ClassroomRepository classroomRepository;
    private final BuildingScopeResolver scopeResolver;

    Classroom requireActiveInScope(Long id, Permission permission) {
        return requireScope(classroomRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Classroom", id)), permission);
    }

    Classroom requireAnyInScope(Long id, Permission permission) {
        return requireScope(Finder.orThrow(classroomRepository::findById, id, "Classroom"), permission);
    }

    List<Classroom> findAllActiveInScope(Permission permission) {
        return classroomRepository.findAll(SoftDeleteSpecifications.<Classroom>active().and(scopeSpec(permission)));
    }

    Page<Classroom> findAll(Specification<Classroom> spec, Permission permission, Pageable pageable) {
        return classroomRepository.findAll(spec.and(scopeSpec(permission)), pageable);
    }

    private Specification<Classroom> scopeSpec(Permission permission) {
        return BuildingScopedSpecifications.withinScope(scopeResolver.scopeFor(permission), "building.id");
    }

    private Classroom requireScope(Classroom classroom, Permission permission) {
        if (!scopeResolver.scopeFor(permission).allows(classroom.getBuilding().getId())) {
            throw ResourceNotFoundException.of("Classroom", classroom.getId());
        }
        return classroom;
    }
}
