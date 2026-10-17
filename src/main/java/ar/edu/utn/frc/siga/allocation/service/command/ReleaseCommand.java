package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;

@NamedInterface("api")
public record ReleaseCommand(List<AllocationTarget> targets, ReleaseReason reason, String note) {
}
