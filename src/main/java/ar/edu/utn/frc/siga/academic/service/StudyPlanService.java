package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import java.util.Optional;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService {

    Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedFalse(Integer planCode, Specialty specialty);

    StudyPlan save(StudyPlan studyPlan);

    FindOrCreateResult<StudyPlan> findOrCreate(Integer planCode, Specialty specialty);
}
