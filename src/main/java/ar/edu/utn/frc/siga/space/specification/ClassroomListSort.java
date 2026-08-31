package ar.edu.utn.frc.siga.space.specification;

import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class ClassroomListSort {

    private static final Map<String, String> ALLOWED = Map.of(
            "id", "id",
            "roomNumber", "roomNumber",
            "capacity", "capacity",
            "building", "building.name",
            "classroomType", "classroomType.description",
            "permissionMode", "permissionMode",
            "observations", "observations",
            "enabled", "deletedAt");

    private static final String TIEBREAKER_PROPERTY = "id";

    private ClassroomListSort() {
    }

    public static Pageable apply(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> orders.add(translate(order)));

        boolean hasTiebreaker = orders.stream().anyMatch(order -> order.getProperty().equals(TIEBREAKER_PROPERTY));
        if (!hasTiebreaker) {
            orders.add(Sort.Order.asc(TIEBREAKER_PROPERTY));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private static Sort.Order translate(Sort.Order order) {
        String jpaProperty = ALLOWED.get(order.getProperty());
        if (jpaProperty == null) {
            throw new SpaceDomainException(
                    "Campo de ordenamiento inválido: '" + order.getProperty() + "'. Válidos: " + ALLOWED.keySet());
        }
        return order.isAscending() ? Sort.Order.asc(jpaProperty) : Sort.Order.desc(jpaProperty);
    }
}
