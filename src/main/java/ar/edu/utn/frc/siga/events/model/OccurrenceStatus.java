package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

/**
 * Máquina de estados de una {@link Occurrence}: responde una sola pregunta —¿necesita aula
 * ahora mismo?. Tener aula NO es un estado acá: es {@code existe fila en asignacion_aula},
 * ortogonal a este enum (ver {@code Allocation}). Las transiciones las dispara
 * {@code events} (release/request-room); ninguna las dispara {@code allocation}.
 */
@NamedInterface("api")
public enum OccurrenceStatus {
    /** Necesita aula. Estado inicial. */
    NEEDS_ROOM,
    /** El aula fue liberada a propósito. Reasignable en cualquier momento. */
    ROOM_RELEASED
}
