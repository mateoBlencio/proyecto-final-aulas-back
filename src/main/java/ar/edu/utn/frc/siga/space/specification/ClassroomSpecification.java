package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.model.Classroom;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Construye la {@link Specification} JPA de búsqueda de aulas a partir de un {@link ClassroomFilter},
 * combinando en AND solo los criterios efectivamente informados.
 */
public class ClassroomSpecification {

    /** Traduce los criterios no nulos de {@code filter} a predicados JPA combinados en AND. */
    public static Specification<Classroom> withFilter(ClassroomFilter filter) {
        return (root, query, cb) -> {
            // El filtro "eliminado = false" ya lo aplica @SQLRestriction en la entidad;
            // acá solo quedan los predicados propios del filtro de búsqueda.
            List<Predicate> predicates = new ArrayList<>();

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
