package ar.edu.utn.frc.siga.events.dto.request;

import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pedido de modificación de un evento único existente: reemplaza materia, comisión, fecha,
 * horario, cantidad de alumnos y descripción. {@code subjectId} es obligatorio salvo para
 * {@code eventType=OTRO}; {@code commissionId} nunca es obligatorio por sí solo, pero no
 * puede venir sin {@code subjectId}. {@code description} es la descripción propia del evento
 * (texto libre). Rechaza si la occurrence ya ocurrió.
 *
 * <p>No incluye aula: la reasignación es responsabilidad de {@code allocation}, en una llamada
 * aparte a {@code PUT /v1/allocations} luego de modificado el evento.
 */
@NamedInterface("api")
public record UpdateUniqueEventRequestDto(
        @NotNull UniqueEventKind eventType,
        Long subjectId,
        Long commissionId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull @Min(1) Integer enrolled,
        String description
) {}
