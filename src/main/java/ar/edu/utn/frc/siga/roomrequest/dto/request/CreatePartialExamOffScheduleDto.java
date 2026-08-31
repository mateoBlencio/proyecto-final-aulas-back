package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Parcial fuera de horario de clases: fecha y franja horaria libres. El front expande "todas /
 * algunas / una" comisión a un ítem por comisión, cada uno con su {@code commissionId}.
 */
public record CreatePartialExamOffScheduleDto(
        RoomRequestType type,
        @Valid @NotNull RequesterInfo requester,
        @NotNull Long subjectId,
        @NotEmpty @Valid List<FreeFormItemDto> items
) implements CreateRoomRequestDto {}
