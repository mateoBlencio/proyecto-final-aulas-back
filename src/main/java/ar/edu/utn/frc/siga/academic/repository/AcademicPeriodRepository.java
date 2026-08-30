package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicPeriodRepository extends SoftDeletableRepository<AcademicPeriod, Long> {

    /**
     * Ve todas las filas (incluidas las borradas) a propósito: lo usa el find-or-create de períodos
     * para reconciliar por clave natural (año + cuatrimestre) sin duplicar filas.
     */
    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);
}
