package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Alta de una solicitud de aula. El cuerpo es polimórfico: el campo {@code type} elige el subtipo
 * concreto, y cada uno declara sólo los campos que su tipo de solicitud realmente exige (misma
 * técnica que {@code events.AcademicEventResponseDto}). La lógica por tipo vive en un handler
 * ({@code roomrequest.handler}), no en un validador monolítico.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateOneTimeRoomChangeDto.class, name = "ONE_TIME_ROOM_CHANGE"),
        @JsonSubTypes.Type(value = CreateRegularRoomChangeDto.class, name = "REGULAR_ROOM_CHANGE"),
        @JsonSubTypes.Type(value = CreatePartialExamInClassDto.class, name = "PARTIAL_EXAM_IN_CLASS"),
        @JsonSubTypes.Type(value = CreatePartialExamOffScheduleDto.class, name = "PARTIAL_EXAM_OFF_SCHEDULE"),
        @JsonSubTypes.Type(value = CreateFinalExamDto.class, name = "FINAL_EXAM"),
        @JsonSubTypes.Type(value = CreateConferenceDto.class, name = "CONFERENCE"),
        @JsonSubTypes.Type(value = CreateOtherDto.class, name = "OTHER")
})
public sealed interface CreateRoomRequestDto
        permits CreateOneTimeRoomChangeDto, CreateRegularRoomChangeDto,
                CreatePartialExamInClassDto, CreatePartialExamOffScheduleDto,
                CreateFinalExamDto, CreateConferenceDto, CreateOtherDto {

    RoomRequestType type();

    RequesterInfo requester();

    List<? extends CreateRoomRequestItemDto> items();

    /** Materia de la solicitud. Nula en conferencia y otro cuando el docente no la informa. */
    default Long subjectId() {
        return null;
    }

    /** Comisión única de la solicitud, para los tipos que trabajan a nivel comisión con un solo valor. */
    default Long commissionId() {
        return null;
    }
}
