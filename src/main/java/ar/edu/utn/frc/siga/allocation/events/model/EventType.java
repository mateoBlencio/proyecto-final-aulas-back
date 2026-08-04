package ar.edu.utn.frc.siga.allocation.events.model;

/**
 * Discriminador de tipo de {@link AcademicEvent}, espejo del discriminador de herencia
 * JPA ({@code tipo_evento}). Se usa para etiquetar el evento en las APIs; la fuente de
 * verdad del tipo real es la subclase concreta ({@link RecurringEvent}/{@link UniqueEvent}).
 */
public enum EventType {
    RECURRING,
    UNIQUE_EVENT
}
