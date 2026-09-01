package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum RevisionKind {
    CREATED,
    MODIFIED,
    DELETED
}
