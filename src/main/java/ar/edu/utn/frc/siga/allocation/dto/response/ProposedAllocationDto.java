package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Una fila del preview automático con los datos que necesita el calendario del front:
 * el evento, las fechas de ocurrencia que ocupa y el aula propuesta. {@code classroom}
 * viaja {@code null} solo cuando la fila pertenece a {@code unresolved} (el solver no
 * encontró aula sin conflicto para ese evento y queda para revisión manual).
 */
public record ProposedAllocationDto(
        AcademicEventResponseDto event,
        List<LocalDate> occurrenceDates,
        ClassroomResponseDto classroom
) {
}
