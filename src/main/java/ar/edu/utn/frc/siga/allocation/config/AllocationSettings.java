package ar.edu.utn.frc.siga.allocation.config;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationSettings {

    int getMaxOverlapMinutes();
}
