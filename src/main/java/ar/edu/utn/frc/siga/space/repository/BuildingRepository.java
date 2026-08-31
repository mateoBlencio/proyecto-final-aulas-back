package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends SoftDeletableRepository<Building, Long> {

    Optional<Building> findByName(String name);

    Optional<Building> findByBuildingCode(Integer buildingCode);

}
