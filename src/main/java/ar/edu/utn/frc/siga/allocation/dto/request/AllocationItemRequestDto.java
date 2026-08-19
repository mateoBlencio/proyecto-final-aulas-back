package ar.edu.utn.frc.siga.allocation.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Un ítem del lote de asignación: a qué ocurrencias aplica y con qué aula.
 *
 * <p>Admite tres formas de apuntar, y la validación de acá es la que garantiza que llegue una sola:
 *
 * <ul>
 *   <li>{@code occurrenceIds} — fechas puntuales, elegidas a mano.</li>
 *   <li>{@code eventId} solo — todas las ocurrencias futuras del evento, desde hoy.</li>
 *   <li>{@code eventId} + {@code from} (+ {@code to}) — el movimiento <b>temporal</b> (con
 *       {@code to}) o <b>permanente</b> (sin {@code to}, hasta que termine el dictado).</li>
 * </ul>
 */
public record AllocationItemRequestDto(
        List<Long> occurrenceIds,
        Long eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate to,
        @NotNull Integer classroomId
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }

    /**
     * El rango solo tiene sentido sobre un evento: una lista de ocurrencias ya es explícita, y
     * acotarla por fechas sería ambiguo. Y un {@code to} sin {@code from} no define ningún rango.
     */
    @AssertTrue(message = "'from' y 'to' solo aplican junto a eventId, y 'to' requiere 'from'")
    private boolean isRangeValid() {
        boolean hasRange = from != null || to != null;
        if (hasRange && eventId == null) {
            return false;
        }
        return to == null || from != null;
    }
}
