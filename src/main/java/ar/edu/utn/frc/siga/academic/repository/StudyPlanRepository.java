package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPlanRepository extends SoftDeletableRepository<StudyPlan, Long> {

    Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedAtIsNull(Integer planCode, Specialty specialty);

    Optional<StudyPlan> findByPlanCodeAndSpecialty(Integer planCode, Specialty specialty);
}
