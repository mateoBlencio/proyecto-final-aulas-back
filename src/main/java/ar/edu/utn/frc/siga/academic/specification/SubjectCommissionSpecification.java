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
                predicates.add(cb.equal(root.get("id").get("subjectId"), filter.subjectId()));
            }
            if (filter.commissionId() != null) {
                predicates.add(cb.equal(root.get("id").get("commissionId"), filter.commissionId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SubjectCommission> available() {
        return (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.isNull(root.get("subject").get("deletedAt")),
                cb.isNull(root.get("subject").get("studyPlan").get("deletedAt")),
                cb.isNull(root.get("commission").get("deletedAt")),
                cb.isNull(root.get("commission").get("academicPeriod").get("deletedAt")));
    }
}
