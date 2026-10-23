package ar.edu.utn.frc.siga.academic.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectCommissionFilter(
        Long subjectId,
        Long commissionId,
        String q
) {
    public SubjectCommissionFilter(Long subjectId, Long commissionId) {
        this(subjectId, commissionId, null);
    }
}
