package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

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

    default Long subjectId() {
        return null;
    }

    default Long commissionId() {
        return null;
    }
}
