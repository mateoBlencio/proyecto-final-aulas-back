package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends SoftDeletableRepository<Classroom, Long> {

    @Query("select c from Classroom c join fetch c.building where c.id in :ids")
    List<Classroom> findAllByIdWithBuilding(@Param("ids") Collection<Long> ids);

    List<Classroom> findAllByRoomNumberAndDeletedAtIsNull(Integer roomNumber);

    Optional<Classroom> findByRoomNumberAndBuildingAndDeletedAtIsNull(Integer roomNumber, Building building);

    List<Classroom> findByBuildingAndDeletedAtIsNull(Building building);

    @Query("select c.id from Classroom c where c.building.id = :buildingId")
    Set<Long> findIdsByBuildingId(@Param("buildingId") Long buildingId);

    @Query("select c from Classroom c join fetch c.building b where c.deletedAt is not null or b.deletedAt is not null")
    List<Classroom> findDeactivatedInSiga();
}
