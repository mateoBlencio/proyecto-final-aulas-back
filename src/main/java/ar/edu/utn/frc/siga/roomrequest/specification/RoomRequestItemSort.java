package ar.edu.utn.frc.siga.roomrequest.specification;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class RoomRequestItemSort {

    private static final Map<String, String> ALLOWED = Map.of(
            "id", "id",
            "date", "date",
            "startTime", "startTime",
            "createdAt", "request.createdAt");

    private static final String TIEBREAKER_PROPERTY = "id";

    private RoomRequestItemSort() {
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
            throw new InvalidRoomRequestException(
                    "Campo de ordenamiento inválido: '" + order.getProperty() + "'. Válidos: " + ALLOWED.keySet());
        }
        // Los pedidos sin fecha (día de dictado) quedan al final del orden ascendente por fecha: es el
        // comportamiento por defecto de Postgres para NULLS en ASC, y el desempate por id lo estabiliza.
        return order.isAscending() ? Sort.Order.asc(jpaProperty) : Sort.Order.desc(jpaProperty);
    }
}
