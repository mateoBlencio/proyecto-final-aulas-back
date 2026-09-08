package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class AcademicPeriodSpecification {

    public static Specification<AcademicPeriod> withFilter(AcademicPeriodFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.year() != null) {
                predicates.add(cb.equal(root.get("year"), filter.year()));
            }
            if (filter.semester() != null) {
                predicates.add(cb.equal(root.get("semester"), filter.semester()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
