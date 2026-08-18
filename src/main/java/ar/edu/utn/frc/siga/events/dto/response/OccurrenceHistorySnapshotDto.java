package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record OccurrenceHistorySnapshotDto(
        Long id,
        Long eventId,
        LocalDate date,
        OccurrenceStatus status
) {
}
