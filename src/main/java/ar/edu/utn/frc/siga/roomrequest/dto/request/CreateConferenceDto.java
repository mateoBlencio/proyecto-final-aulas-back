package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Conferencia: materia y comisión opcionales (si vienen, se validan); fecha y franja horaria libres. */
public record CreateConferenceDto(
        RoomRequestType type,
        @Valid @NotNull RequesterInfo requester,
        Long subjectId,
        Long commissionId,
        @NotEmpty @Valid List<FreeFormItemDto> items
) implements CreateRoomRequestDto {}
