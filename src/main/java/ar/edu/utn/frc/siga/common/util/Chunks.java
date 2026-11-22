package ar.edu.utn.frc.siga.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class Chunks {

    private Chunks() {
    }

    /**
     * Drops nulls and duplicates (keeping first-appearance order), splits {@code items} into chunks of at
     * most {@code size}, applies {@code fetch} to each chunk and concatenates the results. Meant for
     * {@code IN} queries that can exceed PostgreSQL's 65,535 parameter limit. Deduplicating up front keeps
     * each row from being returned twice when a repeated id lands in two different chunks. Empty input runs
     * no query and returns an empty list.
     */
    public static <T, R> List<R> query(Collection<T> items, int size, Function<List<T>, List<R>> fetch) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0: " + size);
        }
        List<T> all = items.stream().filter(Objects::nonNull).distinct().toList();
        List<R> result = new ArrayList<>();
        for (int from = 0; from < all.size(); from += size) {
            result.addAll(fetch.apply(all.subList(from, Math.min(from + size, all.size()))));
        }
        return result;
    }
}
