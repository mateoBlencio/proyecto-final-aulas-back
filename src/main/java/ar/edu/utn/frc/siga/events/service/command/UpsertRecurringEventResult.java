package ar.edu.utn.frc.siga.events.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record UpsertRecurringEventResult(Long eventId, boolean created, boolean updated) {}
