package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.BlockerKind;
import ar.edu.utn.frc.siga.events.model.EventType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Evento que está ocupando el aula pedida")
public record ImpactBlockerDto(
        BlockerKind kind,
        Long eventId,
        EventType eventType,
        Long occurrenceId,
        Long allocationId) {
}
