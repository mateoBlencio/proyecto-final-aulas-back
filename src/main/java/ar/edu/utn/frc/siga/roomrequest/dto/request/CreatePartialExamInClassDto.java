package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Parcial que se toma en horario de clases: el docente marca el/los día(s) de cursado; el horario sale del cursado. */
public record CreatePartialExamInClassDto(
        RoomRequestType type,
        @Valid @NotNull RequesterInfo requester,
        @NotNull Long subjectId,
        @NotNull Long commissionId,
        @NotEmpty @Valid List<ScheduledItemDto> items
) implements CreateRoomRequestDto {}
