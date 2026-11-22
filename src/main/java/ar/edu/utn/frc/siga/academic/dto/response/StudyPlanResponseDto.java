package ar.edu.utn.frc.siga.academic.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record StudyPlanResponseDto(
        Long id,
        Integer planCode,
        SpecialtyResponseDto specialty,
        boolean enabled,
        UnavailableReason unavailableReason
) {
    public StudyPlanResponseDto(Long id, Integer planCode, SpecialtyResponseDto specialty, boolean enabled) {
        this(id, planCode, specialty, enabled, null);
    }
}
