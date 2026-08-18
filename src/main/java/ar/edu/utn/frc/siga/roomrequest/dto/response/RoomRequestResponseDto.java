package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * La cabecera no expone estado ni decisión: cada pedido se decide por separado,
 * así que esos datos viven en {@link RoomRequestItemResponseDto}. Los ítems
 * vienen siempre completos, con lo cual un resumen del tipo "2 de 3 resueltos"
 * se calcula en el front sin necesidad de un campo agregado que pueda quedar
 * desincronizado.
 */
public record RoomRequestResponseDto(
        Long id,
        RoomRequestType type,
        AcademicScope scope,
        String teacherName,
        String teacherEmail,
        String teacherPhone,
        SubjectResponseDto subject,
        LocalDateTime createdAt,
        List<RoomRequestItemResponseDto> items
) {}
