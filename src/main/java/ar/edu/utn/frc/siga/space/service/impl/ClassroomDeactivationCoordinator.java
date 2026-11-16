package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import ar.edu.utn.frc.siga.space.service.ClassroomDeactivationHandler;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
class ClassroomDeactivationCoordinator {

    private final ObjectProvider<ClassroomDeactivationHandler> handlers;

    DeactivationImpactDto preview(DeactivationTarget target, Set<Long> classroomIds) {
        return run(target, classroomIds, ClassroomDeactivationHandler::preview);
    }

    DeactivationImpactDto apply(DeactivationTarget target, Set<Long> classroomIds) {
        return run(target, classroomIds, ClassroomDeactivationHandler::apply);
    }

    private DeactivationImpactDto run(DeactivationTarget target, Set<Long> classroomIds,
            BiFunction<ClassroomDeactivationHandler, ClassroomDeactivation, DeactivationImpactDto> action) {
        if (classroomIds.isEmpty()) {
            return DeactivationImpactDto.EMPTY;
        }
        ClassroomDeactivation deactivation = new ClassroomDeactivation(classroomIds, target);
        return handlers.orderedStream()
                .map(handler -> action.apply(handler, deactivation))
                .reduce(DeactivationImpactDto.EMPTY, DeactivationImpactDto::plus);
    }
}
