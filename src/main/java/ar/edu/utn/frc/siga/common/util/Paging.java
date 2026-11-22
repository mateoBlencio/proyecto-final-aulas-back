package ar.edu.utn.frc.siga.common.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;

import java.util.List;
import java.util.Set;

public final class Paging {

    private Paging() {
    }

    /** Rechaza con 400 un orden por una propiedad fuera de la lista permitida. */
    public static void requireSortable(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), allowed);
            }
        }
    }

    public static <T> Page<T> of(List<T> items, Pageable pageable) {
        int start = (int) Math.min(pageable.getOffset(), items.size());
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}