package ar.edu.utn.frc.siga.roomrequest.direct.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDirectEventRequestDto(
        @NotNull @Valid DirectEventDraftDto draft,
        @NotEmpty @Size(max = 100) List<@NotNull Long> classroomIds,
        @Size(max = 64) String suggestionId,
        @NotBlank @Size(max = 500) String reason
) {}
