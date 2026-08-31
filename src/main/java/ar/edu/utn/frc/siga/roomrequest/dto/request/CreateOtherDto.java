package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOtherDto(
        RoomRequestType type,
        @Valid @NotNull RequesterInfo requester,
        Long subjectId,
        Long commissionId,
        @NotEmpty @Valid List<FreeFormItemDto> items
) implements CreateRoomRequestDto {}
