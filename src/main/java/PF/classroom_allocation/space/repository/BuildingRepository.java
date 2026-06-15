package PF.classroom_allocation.space.repository;

import PF.classroom_allocation.space.model.Building;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Integer> {

    Optional<Building> findByIdAndDeletedFalse(Integer id);

}
