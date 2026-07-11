package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a datos de {@link Commission}. */
@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {
    Optional<Commission> findByCourseCodeAndCommissionNumberAndAcademicPeriod(
            String courseCode, Integer commissionNumber, AcademicPeriod academicPeriod);

    /**
     * {@code CommissionMapper} aplana {@code academicPeriod} en el DTO de respuesta; con
     * {@code academicPeriod} ahora LAZY, se compensa con un fetch join para no generar N+1 al
     * mapear listas (p. ej. {@code findByIds}).
     */
    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    Optional<Commission> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"academicPeriod"})
    List<Commission> findAllById(Iterable<Long> ids);
}
