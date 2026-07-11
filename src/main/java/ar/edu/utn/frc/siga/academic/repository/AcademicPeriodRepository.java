package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {
    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);

    /** Períodos académicos activos, para resolver el rango por defecto de {@code /allocations/problems}. */
    List<AcademicPeriod> findByActiveTrue();
}
