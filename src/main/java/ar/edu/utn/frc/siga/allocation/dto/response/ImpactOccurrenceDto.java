package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una clase que el pedido va a tocar, con de dónde sale y a dónde va.
 *
 * @param currentClassroomId  aula actual, o {@code null} si la clase todavía no tiene aula
 * @param blocked             {@code true} si esta clase aparece en {@code conflicts}; se expone
 *                            para poder pintar la fila sin cruzar las dos listas a mano
 */
@Schema(description = "Clase alcanzada por el pedido, con su aula actual y la pedida")
public record ImpactOccurrenceDto(
        Long occurrenceId,
        Long eventId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer currentClassroomId,
        Integer requestedClassroomId,
        boolean blocked) {
}
