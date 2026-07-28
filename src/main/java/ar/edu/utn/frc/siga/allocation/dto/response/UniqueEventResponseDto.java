package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.UniqueEventKind;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Vista de respuesta de un {@code UniqueEvent}: evento que ocurre una sola vez en {@code date}.
 * {@code status}/{@code classroom}/{@code overcrowdedBy} describen su única occurrence: aula
 * asignada (null si aún no tiene), estado (SCHEDULED/ASSIGNED/CANCELLED) y sobrecupo (0 si entra).
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
        CommissionResponseDto commission,
        OccurrenceStatus status,
        ClassroomResponseDto classroom,
        Integer overcrowdedBy,
        String observation
) implements AcademicEventResponseDto {
}
