package ar.edu.utn.frc.siga.space.dto;

public record ClassroomFilter(
    Integer roomNumber,
    Long buildingId,
    Long classroomTypeId,
    Integer capacityMin,
    Integer capacityMax,
    boolean notInSysacad,
    String q
) {
    public ClassroomFilter(Integer roomNumber, Long buildingId, Long classroomTypeId, Integer capacityMin,
            Integer capacityMax, boolean notInSysacad) {
        this(roomNumber, buildingId, classroomTypeId, capacityMin, capacityMax, notInSysacad, null);
    }
}
