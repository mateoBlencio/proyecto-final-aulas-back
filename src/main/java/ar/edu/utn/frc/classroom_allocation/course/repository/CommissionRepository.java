package ar.edu.utn.frc.classroom_allocation.course.repository;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

    Optional<Commission> findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            String codigoCurso, Integer numeroComision, AcademicPeriod periodo);
}
