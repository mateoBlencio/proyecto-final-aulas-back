package ar.edu.utn.frc.classroom_allocation.course.repository;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {

    Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester);
}
