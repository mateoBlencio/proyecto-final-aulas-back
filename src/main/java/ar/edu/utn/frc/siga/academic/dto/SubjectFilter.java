package ar.edu.utn.frc.siga.academic.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectFilter(
        Integer code,
        String name,
        Integer specialtyCode,
        Long studyPlanId
) {
}
