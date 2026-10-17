package ar.edu.utn.frc.siga.allocation.dto.request;

import java.util.List;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReleaseBatchRequestDto(
        @NotEmpty @Valid List<DeallocationTargetRequestDto> items,
        @NotNull ReleaseReason reason,
        @Size(max = 255) String note
) {
    @AssertTrue(message = "MIRROR_RETIRED es un motivo interno y no se puede elegir")
    private boolean isReasonSelectable() {
        return reason != ReleaseReason.MIRROR_RETIRED;
    }

    @AssertTrue(message = "El motivo OTHER requiere una nota")
    private boolean isNoteProvidedForOther() {
        return reason != ReleaseReason.OTHER || (note != null && !note.isBlank());
    }
}
