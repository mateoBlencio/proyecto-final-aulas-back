package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {
}
