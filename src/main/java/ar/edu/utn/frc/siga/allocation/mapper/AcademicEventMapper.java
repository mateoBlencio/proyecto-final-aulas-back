package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapea cada subtipo de {@code AcademicEvent} a su DTO sellado correspondiente. El
 * despacho polimórfico (¿qué subtipo es?) y la resolución de datos ajenos (materia,
 * comisión, aula, estado de la ocurrencia) los hace {@link AcademicEventComposer}, que
 * conoce el tipo concreto vía {@code instanceof} y llama al método específico: acá solo
 * vive el mapeo puro campo a campo.
 */
@Mapper(config = CentralMapperConfig.class)
public interface AcademicEventMapper {

    // "subject"/"commission" se fuerzan a mapear el parámetro entero: el evento solo
    // tiene sus ids (Long), los DTOs siempre vienen resueltos por el composer.
    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "RECURRING")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "commission", source = "commission")
    RecurringEventResponseDto toDto(RecurringEvent event, SubjectResponseDto subject, CommissionResponseDto commission);

    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "UNIQUE_EVENT")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "classroom", source = "classroom")
    @Mapping(target = "overcrowdedBy", source = "overcrowdedBy")
    @Mapping(target = "observation", source = "observation")
    UniqueEventResponseDto toDto(UniqueEvent event, OccurrenceStatus status, ClassroomResponseDto classroom,
            Integer overcrowdedBy, String observation);
}
