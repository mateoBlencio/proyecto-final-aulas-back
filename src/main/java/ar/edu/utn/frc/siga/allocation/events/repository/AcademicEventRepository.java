package ar.edu.utn.frc.siga.allocation.events.repository;

import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a {@code AcademicEvent} (raíz de la jerarquía {@code RecurringEvent}/{@code UniqueEvent}). */
@Repository
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {
}
