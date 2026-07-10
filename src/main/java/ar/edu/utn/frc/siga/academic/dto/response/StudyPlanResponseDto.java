package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record StudyPlanResponseDto(
        Integer planCode,
        SpecialtyResponseDto specialty
) {
}
