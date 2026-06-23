package ar.edu.utn.frc.classroom_allocation.allocation.repository;

import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {
}
