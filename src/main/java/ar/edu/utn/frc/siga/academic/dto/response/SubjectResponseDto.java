package ar.edu.utn.frc.siga.academic.dto.response;

import lombok.Builder;
import lombok.Value;
import org.springframework.modulith.NamedInterface;

/**
 * Representación pública de una materia, con plan y especialidad aplanados
 * (mismo criterio que ClassroomResponseDTO con edificio y tipo de aula).
 */
@NamedInterface("api")
@Value
@Builder
public class SubjectResponseDto {
    Long id;
    Integer code;
    String name;
    String term;
    StudyPlanResponseDto studyPlan;
}
