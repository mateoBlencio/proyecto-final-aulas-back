package ar.edu.utn.frc.siga.roomrequest.dto.response;

/**
 * Opción de aula para el combo público del formulario.
 *
 * <p>A propósito expone lo mínimo (identificación y edificio) y no capacidad,
 * disponibilidad ni tipo: el catálogo completo de aulas es información para
 * personal interno, y este endpoint es público.
 */
public record ClassroomOptionDto(
        Long id,
        Integer roomNumber,
        String buildingName
) {}
