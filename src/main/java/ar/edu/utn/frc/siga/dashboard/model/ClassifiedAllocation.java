package ar.edu.utn.frc.siga.dashboard.model;

/** Category of an allocation's group and the share (1/roomCount) of the group it carries. */
public record ClassifiedAllocation(AllocationCategory category, double weight) {
}
