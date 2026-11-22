package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public class RoomRequestItemCountRepositoryImpl implements RoomRequestItemCountRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<RoomRequestStatus, Long> countByStatus(Specification<RoomRequestItem> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<RoomRequestItem> root = query.from(RoomRequestItem.class);
        query.multiselect(root.get("status").alias("status"), cb.count(root).alias("total"))
                .where(spec.toPredicate(root, query, cb))
                .groupBy(root.get("status"));

        Map<RoomRequestStatus, Long> counts = new HashMap<>();
        for (Tuple row : em.createQuery(query).getResultList()) {
            counts.put(row.get("status", RoomRequestStatus.class), row.get("total", Long.class));
        }
        return counts;
    }
}
