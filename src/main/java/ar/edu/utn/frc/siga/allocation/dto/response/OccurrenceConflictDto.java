package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Conflicto de reasignación: una ocurrencia del pedido que solapa con otra cosa en la misma
 * aula, fecha y franja.
 *
 * @param conflictingOccurrenceId ocurrencia que está bloqueando. Es lo que permite armar el lote
 *        que la corre de aula para destrabar el pedido, sin tener que resolver la asignación
 *        bloqueante con una llamada aparte.
 * @param conflictingAllocationId asignación bloqueante, o {@code null} si el bloqueo viene de otro
 *        item del mismo lote — ahí todavía no hay nada escrito, y lo que hay que corregir es el
 *        pedido, no el estado del sistema.
 */
@Schema(description = "Conflicto de reasignación: ocurrencia que solapa con una asignación existente")
public record OccurrenceConflictDto(
        Long occurrenceId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer classroomId,
        Long conflictingEventId,
        Long conflictingAllocationId,
        Long conflictingOccurrenceId) {
}
