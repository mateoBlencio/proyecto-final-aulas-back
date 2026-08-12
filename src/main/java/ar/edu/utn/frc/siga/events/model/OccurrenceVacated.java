package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

/** Publicado cuando una occurrence libera su aula a propósito (ver OccurrenceService#release). */
@NamedInterface("api")
public record OccurrenceVacated(Long occurrenceId) {
}
