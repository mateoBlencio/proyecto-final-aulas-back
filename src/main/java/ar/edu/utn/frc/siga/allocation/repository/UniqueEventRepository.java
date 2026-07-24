package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a {@code UniqueEvent} (eventos que ocurren una sola vez: parciales, TPs, mesas especiales). */
@Repository
public interface UniqueEventRepository extends JpaRepository<UniqueEvent, Long> {
}
