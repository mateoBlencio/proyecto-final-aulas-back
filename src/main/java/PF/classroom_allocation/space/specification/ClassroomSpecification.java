package PF.classroom_allocation.space.specification;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.model.Classroom;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class ClassroomSpecification {

    public static Specification<Classroom> withFilter(ClassroomFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("deleted")));

            if (filter.roomNumber() != null && !filter.roomNumber().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("roomNumber")),
                        "%" + filter.roomNumber().toLowerCase() + "%"));
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
            if (filter.floor() != null) {
                predicates.add(cb.equal(root.get("floor"), filter.floor()));
            }
            if (filter.available() != null) {
                predicates.add(cb.equal(root.get("available"), filter.available()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
