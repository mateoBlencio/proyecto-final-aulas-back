package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum OccurrenceStatus {
    NEEDS_ROOM,
    ROOM_RELEASED
}
