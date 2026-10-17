package ar.edu.utn.frc.siga.events.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OccurrenceRelease(Long occurrenceId, Long previousClassroomId) {}
