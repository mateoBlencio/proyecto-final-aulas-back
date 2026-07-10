package ar.edu.utn.frc.siga.common.dto;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resultado uniforme de una operación "buscar o crear": el valor resuelto y si fue
 * recién creado ({@code created = true}) o ya existía ({@code created = false}).
 * <p>
 * {@link #resolve} colapsa el patrón repetido {@code Optional.map(...).orElseGet(...)}
 * a una línea. {@link #map} transforma el valor preservando el flag {@code created} —
 * típicamente para pasar de entidad a DTO en la frontera pública de un módulo, ya que
 * {@code value} puede contener tanto una entidad (uso interno) como un DTO (uso en las
 * fachadas {@code api}).
 */
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
