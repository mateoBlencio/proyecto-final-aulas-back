package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;

public record AssignedClassroomDto(
        Long id,
        Integer roomNumber,
        String buildingName,
        Integer capacity,
        UnavailableReason unavailableReason
) {

    public AssignedClassroomDto(Long id, Integer roomNumber, String buildingName, Integer capacity) {
        this(id, roomNumber, buildingName, capacity, null);
    }
}
