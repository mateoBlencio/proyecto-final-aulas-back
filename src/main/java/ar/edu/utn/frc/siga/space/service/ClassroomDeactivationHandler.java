package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface ClassroomDeactivationHandler {

    DeactivationImpactDto preview(ClassroomDeactivation deactivation);

    DeactivationImpactDto apply(ClassroomDeactivation deactivation);
}
