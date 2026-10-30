package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class CommissionSpecification {

    public static Specification<Commission> withFilter(CommissionFilter filter) {
        return withFilter(filter, true);
    }

    // With includeDeactivated=false, the subjects that make a commission "offered" must also hang from an active
    // study plan and specialty; otherwise a commission would be grouped under a specialty that is deactivated.
    public static Specification<Commission> withFilter(CommissionFilter filter, boolean includeDeactivated) {
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
                offeredPredicates.add(cb.equal(subjectCommission.get("commission").get("id"), root.get("id")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("deletedAt")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("subject").get("deletedAt")));
                if (!includeDeactivated) {
                    offeredPredicates.add(cb.isNull(subjectCommission.get("subject").get("studyPlan").get("deletedAt")));
                    offeredPredicates.add(cb.isNull(
                            subjectCommission.get("subject").get("studyPlan").get("specialty").get("deletedAt")));
                }
                if (filter.subjectId() != null) {
                    offeredPredicates.add(cb.equal(subjectCommission.get("subject").get("id"), filter.subjectId()));
                }
                if (filter.specialtyCode() != null) {
                    offeredPredicates.add(cb.equal(
                            subjectCommission.get("subject").get("studyPlan").get("specialty").get("specialtyCode"),
                            filter.specialtyCode()));
                }
                offered.select(subjectCommission.get("commission").get("id"))
                        .where(offeredPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(offered));
            }
            if (Boolean.TRUE.equals(filter.withoutSpecialty())) {
                Subquery<Long> offered = query.subquery(Long.class);
                var subjectCommission = offered.from(SubjectCommission.class);
                offered.select(subjectCommission.get("commission").get("id"))
                        .where(cb.equal(subjectCommission.get("commission").get("id"), root.get("id")),
                                cb.isNull(subjectCommission.get("deletedAt")),
                                cb.isNull(subjectCommission.get("subject").get("deletedAt")),
                                includeDeactivated ? cb.conjunction() : cb.and(
                                        cb.isNull(subjectCommission.get("subject").get("studyPlan").get("deletedAt")),
                                        cb.isNull(subjectCommission.get("subject").get("studyPlan").get("specialty")
                                                .get("deletedAt"))));
                predicates.add(cb.not(cb.exists(offered)));
            }
            var ranks = FuzzySearch.ranks(filter.q(), token -> CatalogSearch.commissionRank(cb, query, root,
                    null, filter.specialtyCode(), token, includeDeactivated));
            Predicate search = FuzzySearch.matchesAll(cb, ranks);
            if (search != null) {
                predicates.add(search);
                FuzzySearch.orderByRelevance(query, cb, ranks, root.get("id"));
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
