package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicPeriodRepository extends SoftDeletableRepository<AcademicPeriod, Long> {

    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);

    List<AcademicPeriod> findByYearAndDeletedAtIsNull(Integer year);

    /** Períodos activos del cuatrimestre dado cuyo receso se cruza con [from, to]. */
    List<AcademicPeriod> findBySemesterAndDeletedAtIsNullAndRecessStartLessThanEqualAndRecessEndGreaterThanEqual(
            Integer semester, LocalDate to, LocalDate from);
}
