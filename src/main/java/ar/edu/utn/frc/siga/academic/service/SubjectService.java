package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import java.util.Optional;

public interface SubjectService {

    Optional<Subject> findByCodeAndStudyPlanAndDeletedFalse(Integer code, StudyPlan studyPlan);

    Optional<Subject> findById(Long id);

    Subject save(Subject subject);

    FindOrCreateResult<Subject> findOrCreate(Integer code, String name, StudyPlan studyPlan, String term);
}
