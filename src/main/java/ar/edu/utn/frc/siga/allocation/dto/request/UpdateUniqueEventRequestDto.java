package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pedido de modificación de un evento único existente: reemplaza fecha, horario, cantidad de
 * alumnos, aula y observaciones. Se revalida disponibilidad, solapamiento y capacidad antes de
 * guardar (mismo camino que el alta).
 */
public record UpdateUniqueEventRequestDto(
        @NotNull @Min(1) Integer enrolled,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull LocalDate date,
        String description,
        @NotNull Integer classroomId,
        String observation
) {}
