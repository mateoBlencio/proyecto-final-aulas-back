package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.Building;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Integer> {

    Optional<Building> findByName(String name);

}
