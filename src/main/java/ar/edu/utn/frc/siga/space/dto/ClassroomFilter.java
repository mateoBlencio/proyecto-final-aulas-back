package ar.edu.utn.frc.siga.space.dto;

public record ClassroomFilter(
    Integer roomNumber,
    Long buildingId,
    Long classroomTypeId,
    Integer capacityMin,
    Integer capacityMax
) {
}
