package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCodeAndStudyPlan(Integer code, StudyPlan studyPlan);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    Optional<Subject> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findAllById(Iterable<Long> ids);

    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findByStudyPlan_Specialty_SpecialtyCode(Integer specialtyCode);
}
