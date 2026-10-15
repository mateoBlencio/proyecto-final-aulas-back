package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.util.List;

public record RoomAlternativeDto(
        ClassroomResponseDto classroom,
        AlternativeReason reason,
        int overlapMinutes,
        Long conflictingEventId,
        int overcrowdedBy,
        List<UnmetRequirementDto> unmetRequirements
) {

    public enum AlternativeReason {
        OVERLAP, SUBJECT_NOT_PERMITTED
    }
}
