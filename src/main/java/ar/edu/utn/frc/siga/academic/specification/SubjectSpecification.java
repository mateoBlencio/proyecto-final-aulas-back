package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.model.Subject;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class SubjectSpecification {

    public static Specification<Subject> withFilter(SubjectFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.code() != null) {
                predicates.add(cb.equal(root.get("code"), filter.code()));
            }
            if (filter.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"));
            }
            if (filter.specialtyCode() != null) {
                predicates.add(cb.equal(
                        root.get("studyPlan").get("specialty").get("specialtyCode"), filter.specialtyCode()));
            }
            if (filter.studyPlanId() != null) {
                predicates.add(cb.equal(root.get("studyPlan").get("id"), filter.studyPlanId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
