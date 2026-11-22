package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import ar.edu.utn.frc.siga.space.service.ClassroomDeactivationHandler;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class AllocationDeactivationHandler implements ClassroomDeactivationHandler {

    private static final String CLASSROOM_NOTE = "Aula deshabilitada";
    private static final String BUILDING_NOTE = "Edificio deshabilitado";

    private final DeactivatedClassroomReleaser releaser;

    @Override
    public DeactivationImpactDto preview(ClassroomDeactivation deactivation) {
        DeactivatedClassroomReleaser.Plan plan = releaser.plan(noteByClassroom(deactivation));
        return new DeactivationImpactDto(plan.releasedClasses(), plan.mergedClasses(), 0, List.of());
    }

    @Override
    public DeactivationImpactDto apply(ClassroomDeactivation deactivation) {
        DeactivatedClassroomReleaser.DeactivationResult result = releaser.apply(noteByClassroom(deactivation));
        return new DeactivationImpactDto(result.releasedClasses(), result.mergedClasses(), 0, List.of());
    }

    private static Map<Long, String> noteByClassroom(ClassroomDeactivation deactivation) {
        String note = deactivation.target() == DeactivationTarget.CLASSROOM ? CLASSROOM_NOTE : BUILDING_NOTE;
        return deactivation.classroomIds().stream().collect(Collectors.toMap(Function.identity(), id -> note));
    }
}
