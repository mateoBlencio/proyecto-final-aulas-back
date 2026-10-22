package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
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
            if (filter.specialtyCode() != null || filter.subjectId() != null) {
                Subquery<Long> offered = query.subquery(Long.class);
                var subjectCommission = offered.from(SubjectCommission.class);
                List<Predicate> offeredPredicates = new ArrayList<>();
                offeredPredicates.add(cb.equal(subjectCommission.get("id").get("commissionId"), root.get("id")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("deletedAt")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("subject").get("deletedAt")));
                if (filter.subjectId() != null) {
                    offeredPredicates.add(cb.equal(subjectCommission.get("id").get("subjectId"), filter.subjectId()));
                }
                if (filter.specialtyCode() != null) {
                    offeredPredicates.add(cb.equal(
                            subjectCommission.get("subject").get("studyPlan").get("specialty").get("specialtyCode"),
                            filter.specialtyCode()));
                }
                offered.select(subjectCommission.get("id").get("commissionId"))
                        .where(offeredPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(offered));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Commission> available() {
        return (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.or(cb.isTrue(root.get("sysacadEnabled")), cb.isNull(root.get("sysacadHash"))),
                cb.isNull(root.get("academicPeriod").get("deletedAt")));
    }
}
