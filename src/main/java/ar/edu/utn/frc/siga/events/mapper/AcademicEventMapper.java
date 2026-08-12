package ar.edu.utn.frc.siga.events.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface AcademicEventMapper {

    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "RECURRING")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "commission", source = "commission")
    RecurringEventResponseDto toDto(RecurringEvent event, SubjectResponseDto subject, CommissionResponseDto commission);

    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "UNIQUE_EVENT")
    @Mapping(target = "eventType", source = "event.kind")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "commission", source = "commission")
    UniqueEventResponseDto toDto(UniqueEvent event, SubjectResponseDto subject, CommissionResponseDto commission);
}
