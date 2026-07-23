package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Un evento que el auto-preview no pudo ubicar en ninguna aula candidata, con los solapes
 * que explican por qué: para cada aula candidata se busca el primer bloqueo (por fecha),
 * primero contra ocupación firme de BD y, si no hay, contra las propuestas ya resueltas del
 * propio preview ({@link MoveConflictDto#origin()} distingue el origen). A lo sumo un
 * conflicto por aula candidata. {@code conflicts} vacío significa que no se pudo determinar
 * el motivo (no debería pasar salvo un solve subóptimo por corte de tiempo).
 */
public record UnresolvedAllocationDto(
        AcademicEventResponseDto event,
        List<LocalDate> dates,
        List<MoveConflictDto> conflicts
) {
}
