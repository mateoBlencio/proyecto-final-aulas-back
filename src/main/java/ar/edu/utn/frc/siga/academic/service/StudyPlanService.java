package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

/** Fachada de planes de estudio: resolución idempotente (find-or-create) por código de plan dentro de una especialidad. */
@NamedInterface("api")
public interface StudyPlanService {

    /** {@code specialtyCode} identifica la especialidad por su clave natural (no hay ID cruzando la frontera). */
    FindOrCreateResult<StudyPlanResponseDto> findOrCreate(Integer planCode, Integer specialtyCode);
}
