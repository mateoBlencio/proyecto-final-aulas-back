package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper puro: solo campos propios (datos de otros módulos los resuelve {@link RoomRequestComposer}, ADR-002).
 * Ojo: varios {@code ignore = true} ({@code status}, {@code items}, {@code preferences}) no significan
 * null, sino "lo pone el {@code @Builder.Default} de la entidad" — sacar ese default rompe en silencio.
 */
@Mapper(config = CentralMapperConfig.class)
public interface RoomRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "glpiTicketId", ignore = true)
    @Mapping(target = "items", ignore = true)
    RoomRequest toEntity(CreateRoomRequestDto dto);

    /**
     * El ítem nace suelto: {@link RoomRequest#addItem} asigna posición y cabecera.
     * {@code duration} va por {@code expression} y no {@code source} porque MapStruct
     * no ve accessors derivados del record, solo sus componentes.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "request", ignore = true)
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "decidedBy", ignore = true)
    @Mapping(target = "decidedAt", ignore = true)
    @Mapping(target = "decisionReason", ignore = true)
    @Mapping(target = "preferences", ignore = true)
    @Mapping(target = "duration", expression = "java(dto.duration())")
    @Mapping(target = "requiresProjector", source = "requiresProjector", defaultValue = "false")
    @Mapping(target = "requiresComputers", source = "requiresComputers", defaultValue = "false")
    RoomRequestItem toEntity(CreateRoomRequestItemDto dto);

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
    @Mapping(target = "durationMinutes",
             expression = "java(item.getDuration() == null ? null : item.getDuration().toMinutes())")
    RoomRequestItemResponseDto toDto(RoomRequestItem item,
                                     CommissionResponseDto commission,
                                     ClassroomOptionDto currentClassroom,
                                     List<ClassroomOptionDto> preferredClassrooms);
}
