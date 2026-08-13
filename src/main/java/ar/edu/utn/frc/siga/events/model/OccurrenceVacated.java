package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OccurrenceVacated(Long occurrenceId) {
}
