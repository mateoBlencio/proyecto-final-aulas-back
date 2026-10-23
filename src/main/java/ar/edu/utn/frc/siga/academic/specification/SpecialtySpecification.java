package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class SpecialtySpecification {

    public static Specification<Specialty> withFilter(SpecialtyFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.specialtyCode() != null) {
                predicates.add(cb.equal(root.get("specialtyCode"), filter.specialtyCode()));
            }
            if (filter.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"));
            }
            if (Boolean.TRUE.equals(filter.hasSubjects())) {
                Subquery<Long> subjectExists = query.subquery(Long.class);
                var subject = subjectExists.from(Subject.class);
                subjectExists.select(subject.get("id"))
                        .where(cb.equal(subject.get("studyPlan").get("specialty"), root),
                                cb.isNull(subject.get("deletedAt")),
                                cb.isNull(subject.get("studyPlan").get("deletedAt")));
                predicates.add(cb.exists(subjectExists));
            }
            Predicate search = FuzzySearch.allTokens(cb, filter.q(), token -> FuzzySearch.matches(cb,
                    FuzzySearch.text(cb, root.get("specialtyCode"), root.get("name"), root.get("abbreviation")),
                    token));
            if (search != null) {
                predicates.add(search);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
