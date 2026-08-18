package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record DeallocationCommand(List<AllocationTarget> targets, String observation) {
}
