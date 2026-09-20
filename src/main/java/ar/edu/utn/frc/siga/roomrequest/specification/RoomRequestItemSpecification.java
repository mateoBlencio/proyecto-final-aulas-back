package ar.edu.utn.frc.siga.roomrequest.specification;

import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

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
            if (filter.requiresSpecialAssignment() != null) {
                // requiresExamUsers es nullable: sin coalesce, NULL OR false da NULL y cb.not(NULL) también
                // es NULL, no true, así que la fila se pierde en ambos sentidos del filtro.
                Predicate special = cb.or(
                        cb.isTrue(root.get("requiresComputers")),
                        cb.isNotNull(root.get("requiredSoftware")),
                        cb.isTrue(cb.coalesce(root.get("requiresExamUsers"), false)));
                predicates.add(filter.requiresSpecialAssignment() ? special : cb.not(special));
            }
            if (filter.derivedBuildingId() != null) {
                predicates.add(cb.equal(root.get("derivedBuildingId"), filter.derivedBuildingId()));
            }
            if (filter.wasReturned() != null) {
                Predicate returned = cb.isNotNull(root.get("returnedFromBuildingId"));
                predicates.add(filter.wasReturned() ? returned : cb.not(returned));
            }
            if (filter.partiallyResolved() != null) {
                Subquery<Long> assignedCount = query.subquery(Long.class);
                Root<RoomRequestItemAllocation> allocation = assignedCount.from(RoomRequestItemAllocation.class);
                assignedCount.select(cb.countDistinct(allocation.get("position")))
                        .where(cb.equal(allocation.get("item"), root));
                Predicate partial = cb.and(
                        cb.greaterThan(assignedCount, 0L),
                        cb.lessThan(assignedCount, root.get("classroomCount").as(Long.class)));
                predicates.add(filter.partiallyResolved() ? partial : cb.not(partial));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
