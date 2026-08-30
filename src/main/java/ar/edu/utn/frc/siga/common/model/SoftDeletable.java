package ar.edu.utn.frc.siga.common.model;

import java.time.Instant;

/**
 * Contrato de borrado lógico. Permite que specifications, fragments de repositorio y mappers razonen
 * contra la abstracción y no contra una clase concreta.
 */
public interface SoftDeletable {

    Instant getDeletedAt();

    boolean isActive();

    void activate();

    void deactivate(Instant when);
}
