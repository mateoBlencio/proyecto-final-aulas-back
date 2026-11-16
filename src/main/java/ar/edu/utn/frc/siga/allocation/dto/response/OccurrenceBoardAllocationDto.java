package ar.edu.utn.frc.siga.allocation.dto.response;

public record OccurrenceBoardAllocationDto(
        Long allocationId,
        Long classroomId,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity
) {
}
