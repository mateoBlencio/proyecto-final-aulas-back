package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchReassignRequestDto(
        @NotEmpty @Valid List<MoveDto> moves
) {
    public record MoveDto(
            @NotNull Long allocationId,
            @NotNull Integer classroomId
    ) {}
}
