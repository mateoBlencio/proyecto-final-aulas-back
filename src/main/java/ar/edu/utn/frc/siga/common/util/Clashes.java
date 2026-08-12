package ar.edu.utn.frc.siga.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Motor genérico de "encontrá los choques": dos cosas chocan si comparten una clave de
 * agrupación (ej. {@link RoomDate}) y sus franjas horarias se solapan
 * ({@link TimeRanges#overlaps}). Cada llamada indexa por clave y, dentro de cada bucket
 * compartido, ordena por {@code startTime} y barre con corte temprano — evita el producto
 * cartesiano de comparar contra TODO el otro conjunto.
 *
 * <p>Un ítem sin claves (ej. {@code classroomId == null}, fila sin aula) simplemente no
 * entra en ningún bucket y nunca choca — sin chequeos especiales en el caller.
 */
public final class Clashes {

    private Clashes() {
    }

    /** Fábrica del conflicto emitido, con la clave por la que {@code a} y {@code b} coincidieron. */
    @FunctionalInterface
    public interface ConflictFactory<A, B, K, C> {
        C create(A a, B b, K key);
    }

    /** Choques entre dos conjuntos distintos (ej. candidatos vs. ocupación firme de BD). */
    public static <A extends TimeSpan, B extends TimeSpan, K, C> List<C> between(
            Collection<A> left, Function<A, ? extends Collection<K>> leftKeys,
            Collection<B> right, Function<B, ? extends Collection<K>> rightKeys,
            BiPredicate<A, B> extra, ConflictFactory<A, B, K, C> emit) {
        Map<K, List<B>> index = indexByKey(right, rightKeys);

        List<C> conflicts = new ArrayList<>();
        for (A a : left) {
            for (K key : leftKeys.apply(a)) {
                List<B> bucket = index.get(key);
                if (bucket == null) continue;
                for (B b : bucket) {
                    if (!b.startTime().isBefore(a.endTime())) break;
                    if (!TimeRanges.overlaps(a.startTime(), a.endTime(), b.startTime(), b.endTime())) continue;
                    if (!extra.test(a, b)) continue;
                    conflicts.add(emit.create(a, b, key));
                }
            }
        }
        return conflicts;
    }

    /** Choques de un conjunto contra sí mismo: cada par se evalúa una vez por clave compartida. */
    public static <A extends TimeSpan, K, C> List<C> within(
            Collection<A> items, Function<A, ? extends Collection<K>> keys,
            BiPredicate<A, A> extra, ConflictFactory<A, A, K, C> emit) {
        Map<K, List<A>> index = indexByKey(items, keys);

        List<C> conflicts = new ArrayList<>();
        for (Map.Entry<K, List<A>> entry : index.entrySet()) {
            List<A> bucket = entry.getValue();
            for (int i = 0; i < bucket.size(); i++) {
                A a = bucket.get(i);
                for (int j = i + 1; j < bucket.size(); j++) {
                    A b = bucket.get(j);
                    if (!b.startTime().isBefore(a.endTime())) break;
                    if (!extra.test(a, b)) continue;
                    conflicts.add(emit.create(a, b, entry.getKey()));
                }
            }
        }
        return conflicts;
    }

    /** Agrupa por clave (un ítem puede caer en varias) y ordena cada bucket por {@code startTime}. */
    private static <T extends TimeSpan, K> Map<K, List<T>> indexByKey(Collection<T> items, Function<T, ? extends Collection<K>> keyFn) {
        Map<K, List<T>> index = new LinkedHashMap<>();
        for (T item : items) {
            for (K key : keyFn.apply(item)) {
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
        }
        index.values().forEach(bucket -> bucket.sort(Comparator.comparing(TimeSpan::startTime)));
        return index;
    }
}
