package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum ReleaseReason {
    VIRTUAL_CLASS,
    CANCELLED,
    MIRROR_RETIRED,
    OTHER
}
