package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCodeAndStudyPlanAndDeletedFalse(Integer code, StudyPlan studyPlan);
}
