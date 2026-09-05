package ar.edu.utn.frc.siga.common.model;

import java.time.Instant;

public interface SoftDeletable {

    Instant getDeletedAt();

    boolean isActive();

    void activate();

    void deactivate(Instant when);
}
