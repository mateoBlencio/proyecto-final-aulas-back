package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Pedido de reasignación en lote: mueve cada allocation listada a un aula distinta. */
public record BatchReassignRequestDto(
        @NotEmpty @Valid List<MoveDto> moves
) {
    /** Un movimiento puntual: la allocation existente y el aula nueva a la que pasa. */
    public record MoveDto(
            @NotNull Long allocationId,
            @NotNull Integer classroomId
    ) {}
}
