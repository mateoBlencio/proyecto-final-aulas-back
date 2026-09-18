package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRequestItemRepository
        extends JpaRepository<RoomRequestItem, Long>, JpaSpecificationExecutor<RoomRequestItem> {

    @Override
    @EntityGraph(attributePaths = "request")
    Page<RoomRequestItem> findAll(Specification<RoomRequestItem> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"request", "preferences"})
    Optional<RoomRequestItem> findWithRequestById(Long id);

    @Query("select i from RoomRequestItem i "
            + "where i.request.type = ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE "
            + "and i.date = :date "
            + "and i.status <> ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus.CANCELLED")
    List<RoomRequestItem> findActiveOffScheduleItemsByDate(@Param("date") LocalDate date);
}
