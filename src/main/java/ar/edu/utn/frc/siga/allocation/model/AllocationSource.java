package ar.edu.utn.frc.siga.allocation.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum AllocationSource {
    MANUAL,
    AUTOMATIC,
    IMPORTED
}
