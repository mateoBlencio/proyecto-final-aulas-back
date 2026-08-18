package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Integer>, JpaSpecificationExecutor<Classroom> {

    Optional<Classroom> findByRoomNumber(String roomNumber);

    List<Classroom> findAllByRoomNumber(String roomNumber);

    Optional<Classroom> findByRoomNumberAndBuilding(String roomNumber, Building building);

    List<Classroom> findByAvailableTrue();
}
