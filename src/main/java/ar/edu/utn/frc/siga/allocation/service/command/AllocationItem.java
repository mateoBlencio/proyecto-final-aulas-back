package ar.edu.utn.frc.siga.allocation.service.command;

import org.springframework.modulith.NamedInterface;

/**
 * Un movimiento del lote: a qué occurrences ({@link AllocationTarget}) se les quiere dar
 * qué aula. Un {@link AllocationCommand} agrupa varios de estos en una sola operación atómica.
 */
@NamedInterface("api")
public record AllocationItem(AllocationTarget target, Integer classroomId) {
}
