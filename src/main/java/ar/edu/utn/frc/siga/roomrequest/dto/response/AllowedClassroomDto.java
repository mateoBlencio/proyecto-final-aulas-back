package ar.edu.utn.frc.siga.roomrequest.dto.response;

public record AllowedClassroomDto(
        Long id,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity,
        boolean available
) {}
