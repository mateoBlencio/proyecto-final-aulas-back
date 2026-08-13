package ar.edu.utn.frc.siga.common.dto;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public record FindOrCreateResult<T>(T value, boolean created) {

    public static <E> FindOrCreateResult<E> resolve(Optional<E> existing, Supplier<E> creator) {
        return existing
                .map(found -> new FindOrCreateResult<>(found, false))
                .orElseGet(() -> new FindOrCreateResult<>(creator.get(), true));
    }

    public <R> FindOrCreateResult<R> map(Function<T, R> fn) {
        return new FindOrCreateResult<>(fn.apply(value), created);
    }
}
