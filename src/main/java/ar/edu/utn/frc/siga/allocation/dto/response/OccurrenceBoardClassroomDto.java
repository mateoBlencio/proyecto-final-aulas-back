package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;

public record OccurrenceBoardClassroomDto(
        Long classroomId,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity,
        UnavailableReason unavailableReason
) {
}
