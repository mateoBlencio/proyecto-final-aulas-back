package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record StudyPlanResponseDto(
        Long id,
        Integer planCode,
        SpecialtyResponseDto specialty
) {
}
