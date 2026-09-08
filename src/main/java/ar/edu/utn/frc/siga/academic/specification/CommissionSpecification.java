package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.model.Commission;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class CommissionSpecification {

    public static Specification<Commission> withFilter(CommissionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.courseCode() != null) {
                predicates.add(cb.like(cb.lower(root.get("courseCode")), "%" + filter.courseCode().toLowerCase() + "%"));
            }
            if (filter.academicPeriodId() != null) {
                predicates.add(cb.equal(root.get("academicPeriod").get("id"), filter.academicPeriodId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
