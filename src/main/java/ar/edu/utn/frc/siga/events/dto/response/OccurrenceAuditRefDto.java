package ar.edu.utn.frc.siga.events.dto.response;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record OccurrenceAuditRefDto(Long occurrenceId, Long eventId, String eventLabel, LocalDate date) {
}
