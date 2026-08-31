package ar.edu.utn.frc.siga.roomrequest.specification;

import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Mismo patrón que {@code space.specification.ClassroomSpecification}. */
public class RoomRequestItemSpecification {

    public static Specification<RoomRequestItem> withFilter(RoomRequestItemFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.types() != null && !filter.types().isEmpty()) {
                predicates.add(root.get("request").get("type").in(filter.types()));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.scope() != null) {
                predicates.add(cb.equal(root.get("request").get("scope"), filter.scope()));
            }
            if (filter.subjectId() != null) {
                predicates.add(cb.equal(root.get("request").get("subjectId"), filter.subjectId()));
            }
            // Los pedidos sin fecha (cambio de aula regular, parcial en horario de clases) se atan a un
            // día de dictado, no vencen y quedan siempre visibles: el corte por fecha no los excluye.
            if (filter.dateFrom() != null) {
                predicates.add(cb.or(root.get("date").isNull(),
                        cb.greaterThanOrEqualTo(root.get("date"), filter.dateFrom())));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.or(root.get("date").isNull(),
                        cb.lessThanOrEqualTo(root.get("date"), filter.dateTo())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
