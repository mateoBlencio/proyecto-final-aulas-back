package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPlanRepository extends SoftDeletableRepository<StudyPlan, Long> {

    /** Lectura de negocio: solo planes activos. */
    Optional<StudyPlan> findByPlanCodeAndSpecialtyAndDeletedAtIsNull(Integer planCode, Specialty specialty);

    /**
     * Ve todas las filas (incluidas las borradas) a propósito: lo usa {@code StudyPlanResolver.findOrCreate}
     * para que una futura reconciliación pueda re-activar un plan en vez de duplicarlo.
     */
    Optional<StudyPlan> findByPlanCodeAndSpecialty(Integer planCode, Specialty specialty);
}
