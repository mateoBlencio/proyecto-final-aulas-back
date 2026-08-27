package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRequestItemRepository
        extends JpaRepository<RoomRequestItem, Long>, JpaSpecificationExecutor<RoomRequestItem> {

    /**
     * {@code request} es {@code @ManyToOne}, así que el fetch join sigue siendo paginable en SQL
     * (a diferencia de {@code items}/{@code preferences}, que son colecciones). Sin este graph, el
     * composer haría un N+1 al leer {@code item.getRequest()} por cada fila.
     */
    @Override
    @EntityGraph(attributePaths = "request")
    Page<RoomRequestItem> findAll(Specification<RoomRequestItem> spec, Pageable pageable);
}
