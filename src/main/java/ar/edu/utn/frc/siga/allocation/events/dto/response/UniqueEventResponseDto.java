package ar.edu.utn.frc.siga.allocation.events.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.events.model.EventType;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Vista de respuesta de un {@code UniqueEvent}: evento que ocurre una sola vez en {@code date}.
 * Sin aula/estado/sobrecupo: esa vista enriquecida la compone {@code allocation}
 * ({@code UniqueEventAllocationResponseDto}), que sabe de asignaciones.
 */
public record UniqueEventResponseDto(
        Long id,
        EventType type,
        UniqueEventKind eventType,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        LocalDate date,
        String description,
        SubjectResponseDto subject,
        CommissionResponseDto commission
) implements AcademicEventResponseDto {
}
