package ar.edu.utn.frc.siga.events.specification;

import ar.edu.utn.frc.siga.events.dto.AcademicEventFilter;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class AcademicEventSpecification {

    public static Specification<AcademicEvent> withFilter(AcademicEventFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.subjectId() != null) {
                predicates.add(cb.equal(root.get("subjectId"), filter.subjectId()));
            }
            if (filter.commissionId() != null) {
                predicates.add(cb.equal(root.get("commissionId"), filter.commissionId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.type(), switch (filter.type()) {
                    case RECURRING -> RecurringEvent.class;
                    case UNIQUE_EVENT -> UniqueEvent.class;
                }));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
