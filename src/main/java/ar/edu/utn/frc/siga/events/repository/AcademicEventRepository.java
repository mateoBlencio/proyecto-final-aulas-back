package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {
}
