package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Un evento dentro de la propuesta del motor, con el aula sugerida.
 *
 * @param currentRoomStretches aulas que el evento usa <b>hoy</b>, en tramos de fechas, y solo
 *        cuando son más de una. Lista vacía es el caso normal: el evento cursa siempre en la misma
 *        aula (o todavía en ninguna) y no hay nada que avisar. Con dos o más tramos, confirmar la
 *        propuesta los unifica en el aula propuesta, y alguno de ellos puede ser un movimiento
 *        deliberado — por eso conviene mostrarlo antes de confirmar. El motor no bloquea por esto:
 *        propone igual y decide la persona.
 */
public record PreviewItemDto(
        AcademicEventResponseDto event,
        List<LocalDate> occurrenceDates,
        ClassroomResponseDto classroom,
        int overcrowdedBy,
        boolean unchanged,
        List<RoomStretchDto> currentRoomStretches
) {
}
