package ar.edu.utn.frc.classroom_allocation.space.repository;

import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Integer> {

    Optional<Building> findByIdAndDeletedFalse(Integer id);

    Optional<Building> findByNameAndDeletedFalse(String name);

    List<Building> findAllByDeletedFalse();

}
