package ar.edu.utn.frc.siga.allocation.dto.request;

import ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pedido de modificación de un evento único existente con aula: actualiza el evento y
 * reasigna {@code classroomId}, revalidando disponibilidad, solapamiento y capacidad antes
 * de guardar. Rechaza si la occurrence ya ocurrió, o si {@code id} no corresponde a un
 * evento único.
 */
public record UpdateUniqueEventAllocationRequestDto(
        @NotNull UniqueEventKind eventType,
        Long subjectId,
        Long commissionId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull @Min(1) Integer enrolled,
        @NotNull Integer classroomId,
        String description
) {}
