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
 * Mapper puro: solo campos propios. Los datos de otros módulos (materia,
 * comisión, aulas) los resuelve {@link RoomRequestComposer} y se los pasa
 * ya armados, siguiendo el patrón mapper + composer (ADR-002).
 *
 * <p>De entrada arma el agregado campo a campo; lo que es responsabilidad de
 * las entidades (posición del ítem, vínculo con la cabecera, preferencias)
 * queda ignorado acá y lo resuelven ellas.
 *
 * <p><b>Ojo con los {@code ignore = true}:</b> varios no significan "queda en
 * null" sino "lo pone el {@code @Builder.Default} de la entidad" — es el caso
 * de {@code status}, {@code items} y {@code preferences}. Si alguien saca uno
 * de esos defaults, el mapper empieza a producir nulls en silencio y el error
 * recién aparece como violación de NOT NULL al insertar.
 */
@Mapper(config = CentralMapperConfig.class)
public interface RoomRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "glpiTicketId", ignore = true)
    @Mapping(target = "items", ignore = true)
    RoomRequest toEntity(CreateRoomRequestDto dto);

    /**
     * El ítem nace suelto: {@link RoomRequest#addItem} le asigna posición y
     * cabecera, y las preferencias las agrega {@link RoomRequestItem#addPreferences}
     * para que se numeren solas.
     *
     * <p>{@code duration} va por {@code expression} y no por {@code source}
     * porque MapStruct sólo ve los componentes del record, no los accessors
     * derivados. Eso ata la expresión al nombre del parámetro: si se renombra
     * {@code dto}, el error sale sobre el código generado.
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
    @Mapping(target = "durationMinutes", expression = "java(item.getDuration().toMinutes())")
    RoomRequestItemResponseDto toDto(RoomRequestItem item,
                                     CommissionResponseDto commission,
                                     ClassroomOptionDto currentClassroom,
                                     List<ClassroomOptionDto> preferredClassrooms);
}
