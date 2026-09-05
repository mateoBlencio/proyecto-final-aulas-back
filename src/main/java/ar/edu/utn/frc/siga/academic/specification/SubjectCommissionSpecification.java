package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class SubjectCommissionSpecification {

    public static Specification<SubjectCommission> withFilter(SubjectCommissionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.subjectId() != null) {
                predicates.add(cb.equal(root.get("subject").get("id"), filter.subjectId()));
            }
            if (filter.commissionId() != null) {
                predicates.add(cb.equal(root.get("commission").get("id"), filter.commissionId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
