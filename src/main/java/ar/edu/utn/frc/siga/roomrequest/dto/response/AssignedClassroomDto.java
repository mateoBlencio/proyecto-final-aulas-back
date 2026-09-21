package ar.edu.utn.frc.siga.roomrequest.dto.response;

public record AssignedClassroomDto(
        Long id,
        Integer roomNumber,
        String buildingName,
        Integer capacity
) {}
