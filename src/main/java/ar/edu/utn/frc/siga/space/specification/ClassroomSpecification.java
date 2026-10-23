package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class ClassroomSpecification {

    public static Specification<Classroom> withFilter(ClassroomFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.roomNumber() != null) {
                predicates.add(cb.equal(root.get("roomNumber"), filter.roomNumber()));
            }
            if (filter.buildingId() != null) {
                predicates.add(cb.equal(root.get("building").get("id"), filter.buildingId()));
            }
            if (filter.classroomTypeId() != null) {
                predicates.add(cb.equal(root.get("classroomType").get("id"), filter.classroomTypeId()));
            }
            if (filter.capacityMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), filter.capacityMin()));
            }
            if (filter.capacityMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("capacity"), filter.capacityMax()));
            }
            if (filter.notInSysacad()) {
                predicates.add(cb.isFalse(root.get("sysacadCurrent")));
                predicates.add(cb.isNotNull(root.get("sysacadHash")));
            }
            if (!FuzzySearch.tokens(filter.q()).isEmpty()) {
                var building = root.join("building", JoinType.LEFT);
                var type = root.join("classroomType", JoinType.LEFT);
                predicates.add(FuzzySearch.allTokens(cb, filter.q(), token -> {
                    Subquery<Long> resourceMatch = query.subquery(Long.class);
                    var resource = resourceMatch.from(ClassroomResource.class);
                    resourceMatch.select(resource.get("id"))
                            .where(cb.equal(resource.get("classroom"), root),
                                    cb.isNull(resource.get("deletedAt")),
                                    FuzzySearch.matches(cb, resource.get("resourceType").get("name"), token));
                    return cb.or(
                            FuzzySearch.matches(cb,
                                    FuzzySearch.text(cb, root.get("roomNumber"), building.get("name"),
                                            type.get("description"), root.get("observations")),
                                    token),
                            cb.exists(resourceMatch));
                }));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Classroom> available() {
        return (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.isNull(root.get("building").get("deletedAt")),
                cb.or(cb.isTrue(root.get("building").get("sysacadEnabled")),
                        cb.isNull(root.get("building").get("sysacadHash"))),
                cb.or(cb.isTrue(root.get("sysacadEnabled")), cb.isNull(root.get("sysacadHash"))));
    }
}
