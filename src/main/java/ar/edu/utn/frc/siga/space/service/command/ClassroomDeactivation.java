package ar.edu.utn.frc.siga.space.service.command;

import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import org.springframework.modulith.NamedInterface;

import java.util.Set;

@NamedInterface("api")
public record ClassroomDeactivation(Set<Long> classroomIds, DeactivationTarget target) {}
