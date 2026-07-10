package ar.edu.utn.frc.siga.academic.dto.response;

import org.springframework.modulith.NamedInterface;

/**
 * Representación pública de una materia, con plan y especialidad aplanados
 * (mismo criterio que ClassroomResponseDto con edificio y tipo de aula).
 */
@NamedInterface("api")
public record SubjectResponseDto(
        Long id,
        Integer code,
        String name,
        String term,
        StudyPlanResponseDto studyPlan
) {
}
