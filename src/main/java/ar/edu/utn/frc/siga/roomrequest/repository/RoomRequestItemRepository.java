package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRequestItemRepository
        extends JpaRepository<RoomRequestItem, Long>, JpaSpecificationExecutor<RoomRequestItem> {

    @Override
    @EntityGraph(attributePaths = "request")
    Page<RoomRequestItem> findAll(Specification<RoomRequestItem> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"request", "preferences"})
    Optional<RoomRequestItem> findWithRequestById(Long id);
}
