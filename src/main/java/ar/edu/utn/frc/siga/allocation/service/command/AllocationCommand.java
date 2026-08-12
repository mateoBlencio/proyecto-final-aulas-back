package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

@NamedInterface("api")
public record AllocationCommand(List<AllocationItem> items, String observation, AllocationSource source) {

    public static AllocationCommand manual(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.MANUAL);
    }

    public static AllocationCommand imported(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.IMPORTED);
    }

    public static AllocationCommand automatic(List<AllocationItem> items) {
        return new AllocationCommand(items, null, AllocationSource.AUTOMATIC);
    }
}
