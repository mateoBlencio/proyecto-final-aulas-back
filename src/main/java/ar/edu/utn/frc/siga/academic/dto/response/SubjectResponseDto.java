package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectResponseDto(
        Long id,
        Integer code,
        String name,
        String term,
        StudyPlanResponseDto studyPlan,
        boolean enabled,
        UnavailableReason unavailableReason
) {
}
