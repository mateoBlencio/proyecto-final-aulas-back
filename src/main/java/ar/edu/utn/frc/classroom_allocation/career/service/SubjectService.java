package ar.edu.utn.frc.classroom_allocation.career.service;

import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import java.util.Optional;

public interface SubjectService {

    Optional<Subject> findByCodeAndStudyPlanAndDeletedFalse(Integer code, StudyPlan studyPlan);

    Subject save(Subject subject);
}
