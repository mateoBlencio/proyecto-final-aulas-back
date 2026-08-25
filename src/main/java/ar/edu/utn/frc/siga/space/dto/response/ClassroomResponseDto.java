package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomResponseDto(
        Long id,
        Integer roomNumber,
        Integer capacity,
        Long buildingId,
        String buildingName,
        Long classroomTypeId,
        String classroomTypeDescription
) {
}
