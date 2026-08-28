package ar.edu.utn.frc.siga.allocation.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record AllocationItem(AllocationTarget target, Long classroomId) {
}
