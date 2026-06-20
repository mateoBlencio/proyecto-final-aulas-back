package ar.edu.utn.frc.classroom_allocation.career.service;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import java.util.Optional;

public interface StudyPlanService {

    Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedFalse(Integer planCode, Specialty specialty);

    StudyPlan save(StudyPlan studyPlan);
}
