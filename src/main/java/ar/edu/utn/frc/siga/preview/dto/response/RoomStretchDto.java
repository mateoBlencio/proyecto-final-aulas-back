package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;

/**
 * Un tramo continuo de fechas en el que un evento cursa en la misma aula.
 *
 * <p>Existe para poder avisar que un evento <b>hoy no tiene una sola aula</b>: si alguien lo movió
 * a otra aula por unas semanas, sus ocurrencias quedan repartidas en dos o más tramos. Confirmar
 * una propuesta del motor colapsa todos esos tramos a un aula única, así que conviene decirlo
 * antes.
 *
 * @param classroom aula del tramo; puede ser {@code null} si el aula fue dada de baja
 * @param from      primera fecha del tramo, inclusive
 * @param to        última fecha del tramo, inclusive
 * @param classes   cuántas ocurrencias caen en el tramo
 */
public record RoomStretchDto(
        ClassroomResponseDto classroom,
        LocalDate from,
        LocalDate to,
        int classes
) {
}
