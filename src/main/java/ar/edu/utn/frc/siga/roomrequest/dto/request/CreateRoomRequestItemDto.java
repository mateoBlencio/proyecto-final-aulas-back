package ar.edu.utn.frc.siga.roomrequest.dto.request;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Un pedido dentro de una solicitud. Hay dos formas según de dónde salen el día y el horario:
 * <ul>
 *   <li>{@link ScheduledItemDto} — cambio de aula y parcial en horario de clases: el backend deriva
 *       día/horario del cursado ({@code events}); el docente sólo marca el día o la fecha.</li>
 *   <li>{@link FreeFormItemDto} — parcial fuera de horario, final, conferencia y otro: el docente
 *       carga fecha y franja horaria.</li>
 * </ul>
 * Los accesos no presentes en una forma devuelven {@code null} por defecto, para que los handlers
 * lean el pedido de manera uniforme.
 */
public sealed interface CreateRoomRequestItemDto permits ScheduledItemDto, FreeFormItemDto {

    default Long commissionId() {
        return null;
    }

    default LocalDate date() {
        return null;
    }

    default DayOfWeek dayOfWeek() {
        return null;
    }

    default LocalTime startTime() {
        return null;
    }

    default LocalTime endTime() {
        return null;
    }

    default Integer estimated() {
        return null;
    }

    Integer classroomCount();

    default Boolean requiresProjector() {
        return null;
    }

    default Boolean requiresComputers() {
        return null;
    }

    default Integer computerCount() {
        return null;
    }

    default Boolean requiresExamUsers() {
        return null;
    }

    default String requiredSoftware() {
        return null;
    }

    default String observations() {
        return null;
    }

    List<Long> preferredClassroomIds();

    /** Duración derivada del rango que carga el docente; {@code null} si el rango no vino (lo deriva el backend). */
    default Duration duration() {
        return (startTime() == null || endTime() == null) ? null : Duration.between(startTime(), endTime());
    }
}
