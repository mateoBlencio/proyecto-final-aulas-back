package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a datos de {@link SubjectCommission}. */
@Repository
public interface SubjectCommissionRepository extends JpaRepository<SubjectCommission, Long> {

    /**
     * {@code SubjectCommissionMapper} incluye la comisión completa (con su período) en el DTO
     * de respuesta; con {@code commission}/{@code commission.academicPeriod} LAZY, se compensa
     * con un fetch join para no generar N+1 al mapear listas.
     */
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommission(Subject subject, Commission commission);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    /** Comisiones vinculadas a una materia (por su ID). */
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_Id(Long subjectId);
}
