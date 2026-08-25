package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.model.Classroom;
import jakarta.persistence.criteria.Predicate;
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
