package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Una clase del pedido que hoy no se podría mover, con lo necesario para destrabarla.
 *
 * @param occurrenceId           la ocurrencia <b>del pedido</b> que queda bloqueada
 * @param requestedClassroomId   el aula que se pidió para ella
 * @param blockedBy              qué la está ocupando
 * @param alternativeClassrooms  aulas habilitadas y libres en esa fecha y franja, para ofrecer
 *                               como destino alternativo. Es una sugerencia calculada sobre el
 *                               estado actual: la validación de verdad sigue estando en el PUT
 */
@Schema(description = "Ocurrencia del pedido que no se puede aplicar, y cómo destrabarla")
public record ImpactConflictDto(
        Long occurrenceId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer requestedClassroomId,
        ImpactBlockerDto blockedBy,
        List<ClassroomResponseDto> alternativeClassrooms) {
}
