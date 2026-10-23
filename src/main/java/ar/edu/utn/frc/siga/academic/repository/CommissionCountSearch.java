package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.specification.CatalogSearch;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Same result as {@code SubjectCommissionRepository.countCommissionsBySpecialtyIds} narrowed by a search text:
 * a commission counts under a specialty when its tokens match under the rule of
 * {@code GET /commissions?specialtyCode=&q=}. One grouped query for the whole page of specialties.
 */
@Component
public class CommissionCountSearch {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Object[]> countBySpecialtyIds(Collection<Long> specialtyIds, String courseCodePattern,
            Long academicPeriodId, String q, boolean includeDeactivated) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<SubjectCommission> link = query.from(SubjectCommission.class);
        Path<?> subject = link.get("subject");
        Path<?> plan = subject.get("studyPlan");
        Path<?> specialty = plan.get("specialty");
        Path<?> commission = link.get("commission");
        Path<?> period = commission.get("academicPeriod");

        List<Predicate> where = new ArrayList<>();
        where.add(specialty.get("id").in(specialtyIds));
        where.add(cb.isNull(link.get("deletedAt")));
        where.add(cb.isNull(subject.get("deletedAt")));
        where.add(cb.like(cb.lower(commission.get("courseCode")), courseCodePattern));
        if (academicPeriodId != null) {
            where.add(cb.equal(period.get("id"), academicPeriodId));
        }
        if (!includeDeactivated) {
            where.add(cb.isNull(commission.get("deletedAt")));
            where.add(cb.or(cb.isTrue(commission.get("sysacadEnabled")), cb.isNull(commission.get("sysacadHash"))));
            where.add(cb.isNull(period.get("deletedAt")));
            where.add(cb.isNull(plan.get("deletedAt")));
            where.add(cb.isNull(specialty.get("deletedAt")));
        }
        where.add(FuzzySearch.matchesAll(cb, FuzzySearch.ranks(q, token -> CatalogSearch.commissionRank(
                cb, query, commission, specialty, null, token, includeDeactivated))));

        query.multiselect(specialty.get("id"), cb.countDistinct(commission.get("id")))
                .where(where.toArray(new Predicate[0]))
                .groupBy(specialty.get("id"));
        return entityManager.createQuery(query).getResultList();
    }
}
