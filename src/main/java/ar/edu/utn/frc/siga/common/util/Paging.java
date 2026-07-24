package ar.edu.utn.frc.siga.common.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Pagina en memoria una lista ya calculada (agregaciones que no se resuelven con una query paginable). */
public final class Paging {

    private Paging() {
    }

    public static <T> Page<T> of(List<T> items, Pageable pageable) {
        int start = (int) Math.min(pageable.getOffset(), items.size());
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}