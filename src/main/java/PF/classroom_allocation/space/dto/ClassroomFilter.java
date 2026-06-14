package PF.classroom_allocation.space.dto;

public record ClassroomFilter(
    String roomNumber,
    Integer buildingId,
    Integer classroomTypeId,
    Integer capacityMin,
    Integer capacityMax,
    Integer floor,
    Boolean available
) {
}
