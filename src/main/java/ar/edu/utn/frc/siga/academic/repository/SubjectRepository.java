package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a datos de {@link Subject}. */
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCodeAndStudyPlan(Integer code, StudyPlan studyPlan);

    /**
     * {@code SubjectMapper} aplana {@code studyPlan} y {@code studyPlan.specialty} en el DTO de
     * respuesta; con {@code studyPlan} ahora LAZY, se compensa con un fetch join para no generar
     * N+1 al mapear listas (p. ej. {@code findByIds}).
     */
    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    Optional<Subject> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"studyPlan", "studyPlan.specialty"})
    List<Subject> findAllById(Iterable<Long> ids);
}
