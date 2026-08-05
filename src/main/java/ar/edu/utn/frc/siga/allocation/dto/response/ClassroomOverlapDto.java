package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dos eventos con asignaciones cuyos horarios se superponen en la misma aula.
 * {@code dates} agrupa todas las fechas en las que el par choca (un choque
 * recurrente semanal aparece en una sola fila con todas sus fechas).
 */
public record ClassroomOverlapDto(
        ClassroomResponseDto classroom,
        AcademicEventResponseDto eventA,
        AcademicEventResponseDto eventB,
        List<LocalDate> dates
) {
}
