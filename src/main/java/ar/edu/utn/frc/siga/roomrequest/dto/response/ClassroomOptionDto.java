package ar.edu.utn.frc.siga.roomrequest.dto.response;

/** Opción de aula para el combo público: expone solo identificación y edificio, nada del catálogo interno. */
public record ClassroomOptionDto(
        Integer id,
        String roomNumber,
        String buildingName
) {}
