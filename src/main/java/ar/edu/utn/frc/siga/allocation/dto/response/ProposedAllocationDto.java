package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Una fila del preview automático con los datos que necesita el calendario del front:
 * el evento, las fechas de ocurrencia que ocupa y el aula propuesta. {@code classroom}
 * viaja {@code null} solo cuando la fila pertenece a {@code unresolved} (el solver no
 * encontró aula sin conflicto para ese evento y queda para revisión manual).
 * {@code overcrowdedBy} es la cantidad de alumnos que exceden la capacidad del aula
 * propuesta (0 si entran todos, o si la fila es {@code unresolved}): el front pinta la
 * alerta de sobrecupo cuando es {@code > 0}. {@code unchanged} es {@code true} cuando el
 * aula propuesta coincide con la que el evento ya tenía asignada antes de correr el
 * solver: el front no debe mostrar esa fila como una reasignación exitosa.
 */
public record ProposedAllocationDto(
        AcademicEventResponseDto event,
        List<LocalDate> occurrenceDates,
        ClassroomResponseDto classroom,
        int overcrowdedBy,
        boolean unchanged
) {
}
