package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomResponseDto(
        Integer id,
        String roomNumber,
        Integer floor,
        Integer capacity,
        Boolean available,
        Integer buildingId,
        String buildingName,
        Integer classroomTypeId,
        String classroomTypeDescription
) {
}
