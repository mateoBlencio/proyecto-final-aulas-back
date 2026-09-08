package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class StudyPlanSpecification {

    public static Specification<StudyPlan> withFilter(StudyPlanFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.planCode() != null) {
                predicates.add(cb.equal(root.get("planCode"), filter.planCode()));
            }
            if (filter.specialtyCode() != null) {
                predicates.add(cb.equal(root.get("specialty").get("specialtyCode"), filter.specialtyCode()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
