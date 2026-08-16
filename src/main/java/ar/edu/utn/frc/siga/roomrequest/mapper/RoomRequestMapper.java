package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper puro: solo campos propios. Los datos de otros módulos (materia,
 * comisión, aulas) los resuelve {@link RoomRequestComposer} y se los pasa
 * ya armados, siguiendo el patrón mapper + composer (ADR-002).
 */
@Mapper(config = CentralMapperConfig.class)
public interface RoomRequestMapper {

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "items", source = "itemDtos")
    RoomRequestResponseDto toDto(RoomRequest request,
                                 SubjectResponseDto subject,
                                 List<RoomRequestItemResponseDto> itemDtos);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "commission", source = "commission")
    @Mapping(target = "currentClassroom", source = "currentClassroom")
    @Mapping(target = "preferredClassrooms", source = "preferredClassrooms")
    @Mapping(target = "endTime", expression = "java(item.endTime())")
    @Mapping(target = "durationMinutes", expression = "java(item.getDuration().toMinutes())")
    RoomRequestItemResponseDto toDto(RoomRequestItem item,
                                     CommissionResponseDto commission,
                                     ClassroomOptionDto currentClassroom,
                                     List<ClassroomOptionDto> preferredClassrooms);
}
