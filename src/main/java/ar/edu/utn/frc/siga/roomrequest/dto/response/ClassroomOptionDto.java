package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;

public record ClassroomOptionDto(
        Long id,
        Integer roomNumber,
        String buildingName,
        UnavailableReason unavailableReason
) {

    public ClassroomOptionDto(Long id, Integer roomNumber, String buildingName) {
        this(id, roomNumber, buildingName, null);
    }
}
