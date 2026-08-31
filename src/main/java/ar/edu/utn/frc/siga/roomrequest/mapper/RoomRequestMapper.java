package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestRowHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Sólo entidad → DTO de respuesta. El armado de la entidad desde el request lo hace cada handler con
 * builders (el request es polimórfico y cada tipo deriva sus campos de forma distinta).
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
    @Mapping(target = "preferredClassrooms", source = "preferredClassrooms")
    @Mapping(target = "endTime", expression = "java(item.endTime())")
    @Mapping(target = "durationMinutes",
             expression = "java(item.getDuration() == null ? null : item.getDuration().toMinutes())")
    RoomRequestItemResponseDto toDto(RoomRequestItem item,
                                     CommissionResponseDto commission,
                                     List<ClassroomOptionDto> preferredClassrooms);

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    RoomRequestRowHeaderDto toRowHeaderDto(RoomRequest request, SubjectResponseDto subject);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "request", source = "requestHeader")
    @Mapping(target = "commission", source = "commission")
    @Mapping(target = "endTime", expression = "java(item.endTime())")
    RoomRequestItemRowDto toRowDto(RoomRequestItem item,
                                   RoomRequestRowHeaderDto requestHeader,
                                   CommissionResponseDto commission);

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    RoomRequestItemDetailHeaderDto toDetailHeaderDto(RoomRequest request, SubjectResponseDto subject);
}
