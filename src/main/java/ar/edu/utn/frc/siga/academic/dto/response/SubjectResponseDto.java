package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectResponseDto(
        Long id,
        Integer code,
        String name,
        String term,
        StudyPlanResponseDto studyPlan
) {
}
