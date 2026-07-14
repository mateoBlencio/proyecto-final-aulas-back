package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/**
 * Representación pública de la relación materia-comisión, con materia y comisión
 * aplanadas a su ID (mismo criterio que {@code ClassroomResponseDto} con edificio).
 */
@NamedInterface("api")
public record SubjectCommissionResponseDto(
        Long id,
        Long subjectId,
        Long commissionId,
        Integer enrolledCount
) {
}
