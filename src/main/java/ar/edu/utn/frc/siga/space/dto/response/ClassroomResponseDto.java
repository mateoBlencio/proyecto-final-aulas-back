package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

/**
 * Representación de un aula, incluyendo los datos desnormalizados de su edificio y tipo.
 * Parte de la interfaz pública ({@code api}) del módulo {@code space}: es el DTO que
 * consumen otros módulos (p. ej. {@code allocation}) en lugar de la entidad JPA.
 */
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
