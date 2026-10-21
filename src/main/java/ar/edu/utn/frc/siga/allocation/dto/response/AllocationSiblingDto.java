package ar.edu.utn.frc.siga.allocation.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record AllocationSiblingDto(
        Long allocationId,
        Long classroomId,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity
) {
}
