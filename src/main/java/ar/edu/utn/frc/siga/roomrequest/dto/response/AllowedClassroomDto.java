package ar.edu.utn.frc.siga.roomrequest.dto.response;

/** {@code computerCount}: "Cantidad de PC" del aula; {@code null} si no tiene el recurso. */
public record AllowedClassroomDto(
        Long id,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Integer capacity,
        boolean available,
        Integer computerCount
) {}
