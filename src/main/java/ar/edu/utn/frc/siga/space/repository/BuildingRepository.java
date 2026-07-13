package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.Building;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link Building}.
 */
@Repository
public interface BuildingRepository extends JpaRepository<Building, Integer> {

    /** Búsqueda por nombre exacto, usada para detectar duplicados y para find-or-create. */
    Optional<Building> findByName(String name);

}
