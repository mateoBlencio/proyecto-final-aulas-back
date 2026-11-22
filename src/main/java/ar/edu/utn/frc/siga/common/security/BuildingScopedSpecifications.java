package ar.edu.utn.frc.siga.common.security;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

public final class BuildingScopedSpecifications {

    private BuildingScopedSpecifications() {}

    public static <T> Specification<T> withinScope(BuildingScope scope, String path) {
        if (scope.isUnrestricted()) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (scope.buildingIds().isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            String[] segments = path.split("\\.");
            Path<Long> attribute = root.get(segments[0]);
            for (int i = 1; i < segments.length; i++) {
                attribute = attribute.get(segments[i]);
            }
            return attribute.in(scope.buildingIds());
        };
    }
}
