package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface StudyPlanService {

    StudyPlan save(StudyPlan studyPlan);

    FindOrCreateResult<StudyPlan> findOrCreate(Integer planCode, Specialty specialty);
}
