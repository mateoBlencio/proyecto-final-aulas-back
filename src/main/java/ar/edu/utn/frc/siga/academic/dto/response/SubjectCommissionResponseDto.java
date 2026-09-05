package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectCommissionResponseDto(
        Long subjectId,
        Long commissionId,
        CommissionResponseDto commission,
        Integer enrolledCount
) {
}
