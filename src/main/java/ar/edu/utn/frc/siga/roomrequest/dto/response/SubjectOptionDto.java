package ar.edu.utn.frc.siga.roomrequest.dto.response;

/** Opción de materia para el combo público del formulario. */
public record SubjectOptionDto(
        Long id,
        Integer code,
        String name
) {}
