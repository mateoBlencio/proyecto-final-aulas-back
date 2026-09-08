package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.model.Building;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class BuildingSpecification {

    public static Specification<Building> withFilter(BuildingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
