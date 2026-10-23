package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

/**
 * Search ranks over the catalog tree Specialty > Commission > Subject (the subject-commission link). A token
 * matches a node when it matches its own fields, a descendant's own fields or an ancestor's own fields.
 */
public final class CatalogSearch {

    private CatalogSearch() {}

    public static Expression<String> specialtyText(CriteriaBuilder cb, Path<?> specialty) {
        return FuzzySearch.text(cb, specialty.get("specialtyCode"), specialty.get("name"),
                specialty.get("abbreviation"));
    }

    public static Expression<String> commissionText(CriteriaBuilder cb, Path<?> commission) {
        Path<?> period = commission.get("academicPeriod");
        return FuzzySearch.text(cb, commission.get("courseCode"), period.get("year"), period.get("semester"));
    }

    public static Expression<String> subjectText(CriteriaBuilder cb, Path<?> subject) {
        return FuzzySearch.text(cb, subject.get("code"), subject.get("name"));
    }

    /** The link and its subject are active, plus the study plan and specialty unless deactivated are included. */
    public static Predicate visibleLink(CriteriaBuilder cb, Root<SubjectCommission> link,
            boolean includeDeactivated) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isNull(link.get("deletedAt")));
        predicates.add(cb.isNull(link.get("subject").get("deletedAt")));
        if (!includeDeactivated) {
            predicates.add(cb.isNull(link.get("subject").get("studyPlan").get("deletedAt")));
            predicates.add(cb.isNull(link.get("subject").get("studyPlan").get("specialty").get("deletedAt")));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Rank of a token for a commission: its own fields, or the specialty and subject of any visible link of the
     * commission. {@code specialtyScope} (a path) or {@code specialtyCode} restrict those links to one specialty,
     * whose own fields then also count for the commission.
     */
    public static Expression<Integer> commissionRank(CriteriaBuilder cb, AbstractQuery<?> parent,
            Path<?> commission, Path<?> specialtyScope, Integer specialtyCode, String token,
            boolean includeDeactivated) {
        Subquery<Integer> links = FuzzySearch.subquery(parent);
        Root<SubjectCommission> link = links.from(SubjectCommission.class);
        Path<?> subject = link.get("subject");
        Path<?> specialty = subject.get("studyPlan").get("specialty");
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(link.get("id").get("commissionId"), commission.get("id")));
        where.add(visibleLink(cb, link, includeDeactivated));
        if (specialtyScope != null) {
            where.add(cb.equal(specialty, specialtyScope));
        }
        if (specialtyCode != null) {
            where.add(cb.equal(specialty.get("specialtyCode"), specialtyCode));
        }
        links.where(where.toArray(new Predicate[0]));
        Expression<Integer> descendants = FuzzySearch.bestOf(cb, links, FuzzySearch.greatest(cb,
                FuzzySearch.rank(cb, specialtyText(cb, specialty), token),
                FuzzySearch.rank(cb, subjectText(cb, subject), token)));
        return FuzzySearch.greatest(cb, FuzzySearch.rank(cb, commissionText(cb, commission), token), descendants);
    }

    /** Rank of a token for a specialty: its own fields, or the commission or subject of any visible link. */
    public static Expression<Integer> specialtyRank(CriteriaBuilder cb, AbstractQuery<?> parent,
            Path<?> specialty, String token, boolean includeDeactivated) {
        Subquery<Integer> links = FuzzySearch.subquery(parent);
        Root<SubjectCommission> link = links.from(SubjectCommission.class);
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(link.get("subject").get("studyPlan").get("specialty"), specialty));
        where.add(visibleLink(cb, link, includeDeactivated));
        if (!includeDeactivated) {
            where.add(cb.isNull(link.get("commission").get("deletedAt")));
            where.add(cb.or(cb.isTrue(link.get("commission").get("sysacadEnabled")),
                    cb.isNull(link.get("commission").get("sysacadHash"))));
            where.add(cb.isNull(link.get("commission").get("academicPeriod").get("deletedAt")));
        }
        links.where(where.toArray(new Predicate[0]));
        Expression<Integer> descendants = FuzzySearch.bestOf(cb, links, FuzzySearch.greatest(cb,
                FuzzySearch.rank(cb, commissionText(cb, link.get("commission")), token),
                FuzzySearch.rank(cb, subjectText(cb, link.get("subject")), token)));
        return FuzzySearch.greatest(cb, FuzzySearch.rank(cb, specialtyText(cb, specialty), token), descendants);
    }

    /** Rank of a token for a link: its subject, its commission or the specialty of its subject. */
    public static Expression<Integer> linkRank(CriteriaBuilder cb, Path<?> link, String token) {
        return FuzzySearch.greatest(cb,
                FuzzySearch.rank(cb, subjectText(cb, link.get("subject")), token),
                FuzzySearch.rank(cb, commissionText(cb, link.get("commission")), token),
                FuzzySearch.rank(cb, specialtyText(cb, link.get("subject").get("studyPlan").get("specialty")),
                        token));
    }
}
