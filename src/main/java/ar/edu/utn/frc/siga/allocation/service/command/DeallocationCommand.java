package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Comando de desasignación en lote: libera el aula de las occurrences apuntadas por cada
 * {@link AllocationTarget}. No se fusiona con {@link AllocationCommand}: no tiene
 * {@code classroomId} ni política de solapamiento —meterlos con {@code null} sería la misma
 * bandera implícita que se sacó del comando de asignación.
 */
@NamedInterface("api")
public record DeallocationCommand(List<AllocationTarget> targets, String observation) {
}
