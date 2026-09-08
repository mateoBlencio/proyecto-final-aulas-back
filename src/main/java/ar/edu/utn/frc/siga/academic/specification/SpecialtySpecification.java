package ar.edu.utn.frc.siga.academic.specification;

import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import jakarta.persistence.criteria.Predicate;
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
