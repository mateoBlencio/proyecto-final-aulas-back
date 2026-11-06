package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.academic.model.SubjectCommissionDeactivationReason;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
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
        UnavailableReason unavailableReason,
        @Schema(description = "Motivo de la baja del vínculo. NO_ENROLLED: el sync de SysAcad lo dio de baja por informar cero inscriptos. null: vínculo activo o dado de baja a mano.",
                allowableValues = {"NO_ENROLLED"}, nullable = true)
        SubjectCommissionDeactivationReason deactivationReason
) {
    public SubjectCommissionResponseDto(Long subjectId, SubjectSummaryDto subject, Long commissionId,
            CommissionResponseDto commission, Integer enrolledCount, boolean inPerson, boolean enabled) {
        this(subjectId, subject, commissionId, commission, enrolledCount, inPerson, enabled, null, null);
    }
}
