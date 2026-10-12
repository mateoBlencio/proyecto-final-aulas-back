package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomResponseDto(
        Long id,
        Integer roomNumber,
        Integer capacity,
        Long buildingId,
        String buildingName,
        Long classroomTypeId,
        String classroomTypeDescription,
        UnavailableReason unavailableReason,
        boolean currentInSysacad
) {
}
