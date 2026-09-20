package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.BuildingOptionDto;
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

@Mapper(config = CentralMapperConfig.class)
public interface RoomRequestMapper {

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "items", source = "itemDtos")
    RoomRequestResponseDto toDto(RoomRequest request,
                                 SubjectResponseDto subject,
                                 List<RoomRequestItemResponseDto> itemDtos);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "commissions", source = "commissions")
    @Mapping(target = "preferredClassrooms", source = "preferredClassrooms")
    @Mapping(target = "assignedClassrooms", source = "assignedClassrooms")
    @Mapping(target = "derivedBuilding", source = "derivedBuilding")
    @Mapping(target = "returnedFromBuilding", source = "returnedFromBuilding")
    @Mapping(target = "endTime", expression = "java(item.endTime())")
    @Mapping(target = "durationMinutes",
             expression = "java(item.getDuration() == null ? null : item.getDuration().toMinutes())")
    RoomRequestItemResponseDto toDto(RoomRequestItem item,
                                     List<CommissionResponseDto> commissions,
                                     List<ClassroomOptionDto> preferredClassrooms,
                                     List<AssignedClassroomDto> assignedClassrooms,
                                     BuildingOptionDto derivedBuilding,
                                     BuildingOptionDto returnedFromBuilding);

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    RoomRequestRowHeaderDto toRowHeaderDto(RoomRequest request, SubjectResponseDto subject);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "request", source = "requestHeader")
    @Mapping(target = "commissions", source = "commissions")
    @Mapping(target = "endTime", expression = "java(item.endTime())")
    @Mapping(target = "derivedBuildingName", source = "derivedBuildingName")
    @Mapping(target = "assignedClassroomCount", source = "assignedClassroomCount")
    @Mapping(target = "requiresSpecialAssignment",
             expression = "java(Boolean.TRUE.equals(item.getRequiresComputers()) "
                     + "|| item.getRequiredSoftware() != null || Boolean.TRUE.equals(item.getRequiresExamUsers()))")
    @Mapping(target = "wasReturned", expression = "java(item.getReturnedFromBuildingId() != null)")
    @Mapping(target = "partiallyResolved",
             expression = "java(assignedClassroomCount != null && assignedClassroomCount > 0 "
                     + "&& assignedClassroomCount < item.getClassroomCount())")
    RoomRequestItemRowDto toRowDto(RoomRequestItem item,
                                   RoomRequestRowHeaderDto requestHeader,
                                   List<CommissionResponseDto> commissions,
                                   String derivedBuildingName,
                                   Integer assignedClassroomCount);

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "subject", source = "subject")
    RoomRequestItemDetailHeaderDto toDetailHeaderDto(RoomRequest request, SubjectResponseDto subject);
}
