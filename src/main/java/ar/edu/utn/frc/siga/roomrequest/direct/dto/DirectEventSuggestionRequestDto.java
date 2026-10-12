package ar.edu.utn.frc.siga.roomrequest.direct.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DirectEventSuggestionRequestDto(
        @NotNull @Valid DirectEventDraftDto draft,
        @Size(max = 200) List<Long> excludedClassroomIds
) {}
