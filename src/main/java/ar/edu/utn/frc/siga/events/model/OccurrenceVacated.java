package ar.edu.utn.frc.siga.events.model;

import ar.edu.utn.frc.siga.audit.AuditCause;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OccurrenceVacated(Long occurrenceId, String originOperationId) implements AuditCause {
}
