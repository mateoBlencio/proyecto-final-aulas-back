package ar.edu.utn.frc.classroom_allocation.course.repository;

import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository extends JpaRepository<SubjectCommission, Long> {

    Optional<SubjectCommission> findByMateriaAndComisionAndDeletedFalse(Subject materia, Commission comision);
}
