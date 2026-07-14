package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/** Representación pública de un plan de estudio, con su especialidad. */
@NamedInterface("api")
public record StudyPlanResponseDto(
        Integer planCode,
        SpecialtyResponseDto specialty
) {
}
