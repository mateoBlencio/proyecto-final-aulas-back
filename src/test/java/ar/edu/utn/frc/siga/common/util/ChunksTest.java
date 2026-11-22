package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Chunks")
class ChunksTest {

    private static final int SIZE = 10_000;

    /** Records the content of each chunk the query receives. */
    private static final class RecordingQuery {
        final List<List<Integer>> batches = new ArrayList<>();

        List<String> apply(List<Integer> batch) {
            batches.add(new ArrayList<>(batch));
            return batch.stream().map(n -> "r" + n).toList();
        }

        List<Integer> batchSizes() {
            return batches.stream().map(List::size).toList();
        }
    }

    private static List<Integer> numbers(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    @Test
    @DisplayName("query: colección vacía no ejecuta la consulta y devuelve lista vacía")
    void queryWithEmptyCollectionRunsNoQuery() {
        RecordingQuery query = new RecordingQuery();

        List<String> result = Chunks.query(List.of(), SIZE, query::apply);

        assertThat(result).isEmpty();
        assertThat(query.batches).isEmpty();
    }

    @Test
    @DisplayName("query: exactamente size elementos ejecutan una sola consulta")
    void queryWithExactlySizeElementsRunsOneQuery() {
        RecordingQuery query = new RecordingQuery();

        List<String> result = Chunks.query(numbers(SIZE), SIZE, query::apply);

        assertThat(query.batchSizes()).containsExactly(SIZE);
        assertThat(result).hasSize(SIZE);
    }

    @Test
    @DisplayName("query: size + 1 elementos ejecutan dos consultas (size y 1)")
    void queryWithSizePlusOneRunsTwoQueries() {
        RecordingQuery query = new RecordingQuery();

        Chunks.query(numbers(SIZE + 1), SIZE, query::apply);

        assertThat(query.batchSizes()).containsExactly(SIZE, 1);
    }

    @Test
    @DisplayName("query: size - 1 elementos ejecutan una sola consulta")
    void queryWithSizeMinusOneRunsOneQuery() {
        RecordingQuery query = new RecordingQuery();

        Chunks.query(numbers(SIZE - 1), SIZE, query::apply);

        assertThat(query.batchSizes()).containsExactly(SIZE - 1);
    }

    @Test
    @DisplayName("query: 25.000 elementos con size 10.000 se parten en 10.000, 10.000 y 5.000")
    void queryWith25000ElementsSplitsIntoThreeChunks() {
        RecordingQuery query = new RecordingQuery();

        Chunks.query(numbers(25_000), SIZE, query::apply);

        assertThat(query.batchSizes()).containsExactly(10_000, 10_000, 5_000);
    }

    @Test
    @DisplayName("query: cada elemento pasa exactamente una vez y el resultado conserva el orden")
    void queryPassesEachElementOnceAndKeepsOrder() {
        RecordingQuery query = new RecordingQuery();
        List<Integer> items = numbers(25_000);

        List<String> result = Chunks.query(items, SIZE, query::apply);

        List<Integer> seen = query.batches.stream().flatMap(List::stream).toList();
        assertThat(seen).containsExactlyElementsOf(items);
        assertThat(result).containsExactlyElementsOf(items.stream().map(n -> "r" + n).toList());
    }

    @Test
    @DisplayName("query: acepta colecciones que no son lista (Set) sin perder ni repetir elementos")
    void queryAcceptsNonListCollection() {
        RecordingQuery query = new RecordingQuery();
        LinkedHashSet<Integer> items = new LinkedHashSet<>(numbers(25));

        List<String> result = Chunks.query(items, 10, query::apply);

        assertThat(query.batchSizes()).containsExactly(10, 10, 5);
        assertThat(result).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("query: una tanda que no devuelve resultados no afecta a las demás")
    void queryWithEmptyChunkResultKeepsOtherChunks() {
        List<Integer> items = numbers(25);

        List<Integer> result = Chunks.query(items, 10, batch ->
                batch.getFirst() == 10 ? List.of() : batch);

        assertThat(result).containsExactlyElementsOf(
                IntStream.range(0, 25).filter(n -> n < 10 || n >= 20).boxed().toList());
    }

    @Test
    @DisplayName("query: un id repetido en tandas distintas se consulta una sola vez y su resultado no se duplica")
    void queryWithIdRepeatedAcrossChunksFetchesItOnce() {
        RecordingQuery query = new RecordingQuery();
        List<Integer> items = new ArrayList<>(numbers(SIZE + 20));
        items.set(10_005, 5);

        List<String> result = Chunks.query(items, SIZE, query::apply);

        List<Integer> seen = query.batches.stream().flatMap(List::stream).toList();
        assertThat(seen).filteredOn(n -> n == 5).hasSize(1);
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(result).filteredOn("r5"::equals).hasSize(1);
        assertThat(result).doesNotHaveDuplicates();
        assertThat(query.batchSizes()).containsExactly(SIZE, 19);
    }

    @Test
    @DisplayName("query: al quitar duplicados conserva el orden de primera aparición")
    void queryWithDuplicatesKeepsFirstAppearanceOrder() {
        RecordingQuery query = new RecordingQuery();

        Chunks.query(List.of(3, 1, 3, 2, 1), 10, query::apply);

        assertThat(query.batches).containsExactly(List.of(3, 1, 2));
    }

    @Test
    @DisplayName("query: los nulls de la colección nunca llegan a fetch")
    void queryWithNullsDoesNotPassThemToFetch() {
        RecordingQuery query = new RecordingQuery();

        List<String> result = Chunks.query(Arrays.asList(1, null, 2, null), 10, query::apply);

        assertThat(query.batches).containsExactly(List.of(1, 2));
        assertThat(result).containsExactly("r1", "r2");
    }

    @Test
    @DisplayName("query: una colección solo con nulls no ejecuta la consulta")
    void queryWithOnlyNullsRunsNoQuery() {
        RecordingQuery query = new RecordingQuery();

        List<String> result = Chunks.query(Arrays.asList(null, null), 10, query::apply);

        assertThat(result).isEmpty();
        assertThat(query.batches).isEmpty();
    }

    @Test
    @DisplayName("query: size 0 o negativo lanza IllegalArgumentException")
    void queryWithNonPositiveSizeThrows() {
        RecordingQuery query = new RecordingQuery();

        assertThatThrownBy(() -> Chunks.query(numbers(3), 0, query::apply))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Chunks.query(numbers(3), -1, query::apply))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(query.batches).isEmpty();
    }
}
