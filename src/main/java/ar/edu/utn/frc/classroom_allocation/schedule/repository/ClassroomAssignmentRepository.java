package ar.edu.utn.frc.classroom_allocation.schedule.repository;

import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomAssignmentRepository extends JpaRepository<ClassroomAssignment, Long> {

    Optional<ClassroomAssignment> findByMateriaComisionAndAulaAndFranja(
            SubjectCommission materiaComision, Classroom aula, TimeSlot franja);
}
