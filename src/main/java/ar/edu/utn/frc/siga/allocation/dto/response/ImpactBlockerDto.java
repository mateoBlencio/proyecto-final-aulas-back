package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.BlockerKind;
import ar.edu.utn.frc.siga.events.model.EventType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Qué está ocupando el aula que se pidió.
 *
 * @param kind          si el bloqueo es contra algo ya asignado o contra otro item del mismo pedido
 * @param eventId       evento que ocupa
 * @param eventType     sirve para decidir qué ofrecerle al usuario: correr un evento único de una
 *                      fecha suele ser inocuo, correr una clase de un recurrente es otra decisión
 * @param occurrenceId  ocurrencia que ocupa; es el id que hay que mandar en {@code occurrenceIds}
 *                      para moverla en el mismo lote
 * @param allocationId  asignación que ocupa, o {@code null} si {@code kind} es {@code SAME_BATCH}
 */
@Schema(description = "Evento que está ocupando el aula pedida")
public record ImpactBlockerDto(
        BlockerKind kind,
        Long eventId,
        EventType eventType,
        Long occurrenceId,
        Long allocationId) {
}
