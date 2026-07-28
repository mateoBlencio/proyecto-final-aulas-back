package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/**
 * Representación pública de la relación materia-comisión. {@code subjectId} queda aplanado
 * a su ID (mismo criterio que {@code ClassroomResponseDto} con edificio); {@code commission}
 * viene completo (con período académico) para no obligar a un segundo request a
 * {@code GET /v1/commissions/{id}} solo para mostrarla — {@code commissionId} se mantiene
 * igual para no romper a quien ya lo usaba como referencia plana.
 */
@NamedInterface("api")
public record SubjectCommissionResponseDto(
        Long id,
        Long subjectId,
        Long commissionId,
        CommissionResponseDto commission,
        Integer enrolledCount
) {
}
