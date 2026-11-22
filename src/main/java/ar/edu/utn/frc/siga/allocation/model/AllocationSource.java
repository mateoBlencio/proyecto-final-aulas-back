package ar.edu.utn.frc.siga.allocation.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum AllocationSource {
    MANUAL,
    AUTOMATIC,
    IMPORTED,
    SYSACAD;

    // The first Sysacad sync or Excel import of a commission mid-year brings past
    // occurrences; rejecting them would leave those slots without allocation.
    public boolean allowsPastOccurrences() {
        return this == IMPORTED || this == SYSACAD;
    }
}
