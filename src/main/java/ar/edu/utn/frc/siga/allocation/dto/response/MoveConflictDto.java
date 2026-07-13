package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un solapamiento detectado al validar el movimiento de un evento a otra aula: la franja
 * de fecha/hora en la que choca, el evento con el que choca y si ese choque es contra una
 * asignación firme de BD o contra otro ítem de la propia propuesta ajustada.
 */
public record MoveConflictDto(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer classroomId,
        Long conflictingEventId,
        ConflictOrigin origin) {

    /** Origen del conflicto: asignación firme de BD, o ítem de la propuesta ajustada del front. */
    public enum ConflictOrigin {
        DATABASE, PREVIEW
    }
}
