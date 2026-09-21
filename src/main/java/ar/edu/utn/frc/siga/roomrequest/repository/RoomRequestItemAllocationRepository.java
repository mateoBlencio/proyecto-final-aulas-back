package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoomRequestItemAllocationRepository extends JpaRepository<RoomRequestItemAllocation, Long> {

    record ItemAssignedCount(Long itemId, Long assignedCount) {}

    /** Cuenta posiciones distintas, no filas: un REGULAR_ROOM_CHANGE repite la misma aula en varias filas. */
    @Query("select new ar.edu.utn.frc.siga.roomrequest.repository."
            + "RoomRequestItemAllocationRepository$ItemAssignedCount("
            + "a.item.id, count(distinct a.position)) "
            + "from RoomRequestItemAllocation a where a.item.id in :itemIds group by a.item.id")
    List<ItemAssignedCount> countAssignedClassroomsByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
