package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRequestRepository extends JpaRepository<RoomRequest, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<RoomRequest> findWithItemsById(Long id);

    /**
     * Solicitudes con al menos un pedido en ese estado. El estado vive en el
     * ítem, así que la bandeja de "solicitudes con algo pendiente" se resuelve
     * por join y no por una columna de la cabecera.
     */
    Page<RoomRequest> findDistinctByItems_Status(RoomRequestStatus status, Pageable pageable);
}
