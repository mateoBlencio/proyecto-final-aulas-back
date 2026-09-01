package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcademicReferenceValidator {

    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;

    public void requireSubject(Long subjectId) {
        if (subjectId == null) {
            throw new InvalidRoomRequestException("subjectId es obligatorio para este tipo de solicitud.");
        }
        subjectService.findById(subjectId);
    }

    public void validateOptionalSubject(Long subjectId) {
        if (subjectId != null) {
            subjectService.findById(subjectId);
        }
    }

    public void requireCommissionOfSubject(Long subjectId, Long commissionId) {
        if (commissionId == null) {
            throw new InvalidRoomRequestException("commissionId es obligatorio para este tipo de solicitud.");
        }
        commissionService.findById(commissionId);
        requireBelongs(subjectId, commissionId);
    }

    private void requireBelongs(Long subjectId, Long commissionId) {
        if (subjectId == null) {
            return;
        }
        try {
            subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId);
        } catch (ResourceNotFoundException e) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " no pertenece a la materia " + subjectId + ".");
        }
    }
}
