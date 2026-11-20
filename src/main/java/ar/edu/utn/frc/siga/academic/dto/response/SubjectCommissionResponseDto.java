package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectCommissionResponseDto(
        Long subjectId,
        SubjectSummaryDto subject,
        Long commissionId,
        CommissionResponseDto commission,
        Integer enrolledCount,
        boolean inPerson,
        boolean enabled,
        UnavailableReason unavailableReason
) {
    public SubjectCommissionResponseDto(Long subjectId, SubjectSummaryDto subject, Long commissionId,
            CommissionResponseDto commission, Integer enrolledCount, boolean inPerson, boolean enabled) {
        this(subjectId, subject, commissionId, commission, enrolledCount, inPerson, enabled, null);
    }
}
