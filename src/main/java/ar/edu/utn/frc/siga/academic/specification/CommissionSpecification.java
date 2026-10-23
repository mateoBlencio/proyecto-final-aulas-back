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
                offeredPredicates.add(cb.equal(subjectCommission.get("id").get("commissionId"), root.get("id")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("deletedAt")));
                offeredPredicates.add(cb.isNull(subjectCommission.get("subject").get("deletedAt")));
                if (!includeDeactivated) {
                    offeredPredicates.add(cb.isNull(subjectCommission.get("subject").get("studyPlan").get("deletedAt")));
                    offeredPredicates.add(cb.isNull(
                            subjectCommission.get("subject").get("studyPlan").get("specialty").get("deletedAt")));
                }
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
            if (Boolean.TRUE.equals(filter.withoutSpecialty())) {
                Subquery<Long> offered = query.subquery(Long.class);
                var subjectCommission = offered.from(SubjectCommission.class);
                offered.select(subjectCommission.get("id").get("commissionId"))
                        .where(cb.equal(subjectCommission.get("id").get("commissionId"), root.get("id")),
                                cb.isNull(subjectCommission.get("deletedAt")),
                                cb.isNull(subjectCommission.get("subject").get("deletedAt")),
                                includeDeactivated ? cb.conjunction() : cb.and(
                                        cb.isNull(subjectCommission.get("subject").get("studyPlan").get("deletedAt")),
                                        cb.isNull(subjectCommission.get("subject").get("studyPlan").get("specialty")
                                                .get("deletedAt"))));
                predicates.add(cb.not(cb.exists(offered)));
            }
            Predicate search = FuzzySearch.allTokens(cb, filter.q(), token -> {
                Subquery<Long> specialtyMatch = query.subquery(Long.class);
                var subjectCommission = specialtyMatch.from(SubjectCommission.class);
                var specialty = subjectCommission.get("subject").get("studyPlan").get("specialty");
                List<Predicate> specialtyPredicates = new ArrayList<>();
                specialtyPredicates.add(cb.equal(subjectCommission.get("id").get("commissionId"), root.get("id")));
                specialtyPredicates.add(cb.isNull(subjectCommission.get("deletedAt")));
                specialtyPredicates.add(cb.isNull(subjectCommission.get("subject").get("deletedAt")));
                if (!includeDeactivated) {
                    specialtyPredicates.add(cb.isNull(subjectCommission.get("subject").get("studyPlan").get("deletedAt")));
                    specialtyPredicates.add(cb.isNull(specialty.get("deletedAt")));
                }
                specialtyPredicates.add(FuzzySearch.matches(cb, specialty.get("name"), token));
                specialtyMatch.select(subjectCommission.get("id").get("commissionId"))
                        .where(specialtyPredicates.toArray(new Predicate[0]));
                var period = root.get("academicPeriod");
                return cb.or(
                        FuzzySearch.matches(cb,
                                FuzzySearch.text(cb, root.get("courseCode"), period.get("year"), period.get("semester")),
                                token),
                        cb.exists(specialtyMatch));
            });
            if (search != null) {
                predicates.add(search);
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
