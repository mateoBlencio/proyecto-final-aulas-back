package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class StudyPlanSpecification {

    public static Specification<StudyPlan> available() {
        return (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.isNull(root.get("specialty").get("deletedAt")));
    }

    public static Specification<StudyPlan> withFilter(StudyPlanFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.planCode() != null) {
                predicates.add(cb.equal(root.get("planCode"), filter.planCode()));
            }
            if (filter.specialtyCode() != null) {
                predicates.add(cb.equal(root.get("specialty").get("specialtyCode"), filter.specialtyCode()));
            }
            Predicate search = FuzzySearch.allTokens(cb, filter.q(), token -> FuzzySearch.matches(cb,
                    FuzzySearch.text(cb, root.get("planCode"), root.get("specialty").get("name"),
                            root.get("specialty").get("abbreviation")),
                    token));
            if (search != null) {
                predicates.add(search);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
