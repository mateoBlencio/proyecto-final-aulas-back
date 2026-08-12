package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Evento asignado a un aula cuya capacidad no alcanza para la cantidad de inscriptos.
 * {@code dates} agrupa todas las fechas en conflicto para el mismo par evento-aula
 * (un evento recurrente con sobrecupo semanal aparece en una sola fila).
 */
public record OvercrowdedConflictDto(
        AcademicEventResponseDto event,
        ClassroomResponseDto classroom,
        int enrolled,
        int capacity,
        int overcrowdedBy,
        List<LocalDate> dates
) implements AllocationConflictDto {
}
