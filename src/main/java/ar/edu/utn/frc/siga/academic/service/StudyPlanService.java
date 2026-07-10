package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService {

    /** {@code specialtyCode} identifica la especialidad por su clave natural (no hay ID cruzando la frontera). */
    FindOrCreateResult<StudyPlanResponseDto> findOrCreate(Integer planCode, Integer specialtyCode);
}
