package ar.edu.utn.frc.siga.space.dto;

/**
 * Criterios opcionales de búsqueda de aulas (todos nullable/combinables) usados por
 * la consulta paginada de {@code GET /classrooms}.
 */
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
