package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends SoftDeletableRepository<Subject, Long> {
    Optional<Subject> findByCodeAndStudyPlanAndDeletedAtIsNull(Integer code, StudyPlan studyPlan);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    Optional<Subject> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findAllById(Iterable<Long> ids);

    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findByStudyPlan_Specialty_SpecialtyCode(Integer specialtyCode);
}
