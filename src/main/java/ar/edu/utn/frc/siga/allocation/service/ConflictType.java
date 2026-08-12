package ar.edu.utn.frc.siga.allocation.service;

/** Qué tipo(s) de conflicto de asignación pedir en {@link AllocationConflictService#findConflicts}. */
public enum ConflictType {
    UNASSIGNED, OVERCROWDED, OVERLAP
}
