package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

/**
 * Discriminador de tipo de {@link AcademicEvent}, espejo del discriminador de herencia
 * JPA ({@code tipo_evento}). Se usa para etiquetar el evento en las APIs; la fuente de
 * verdad del tipo real es la subclase concreta ({@link RecurringEvent}/{@link UniqueEvent}).
 */
@NamedInterface("api")
public enum EventType {
    RECURRING,
    UNIQUE_EVENT
}
