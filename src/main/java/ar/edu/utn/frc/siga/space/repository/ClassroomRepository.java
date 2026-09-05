package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends SoftDeletableRepository<Classroom, Long> {

    Optional<Classroom> findByRoomNumberAndDeletedAtIsNull(Integer roomNumber);

    List<Classroom> findAllByRoomNumberAndDeletedAtIsNull(Integer roomNumber);

    Optional<Classroom> findByRoomNumberAndBuildingAndDeletedAtIsNull(Integer roomNumber, Building building);

    List<Classroom> findByBuildingAndDeletedAtIsNull(Building building);
}
