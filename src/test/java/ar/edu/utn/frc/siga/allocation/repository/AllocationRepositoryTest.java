package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AllocationRepository (default methods)")
class AllocationRepositoryTest {

    /** CALLS_REAL_METHODS runs the real default method; each test stubs the abstract derived query. */
    private final AllocationRepository repository = mock(AllocationRepository.class, CALLS_REAL_METHODS);

    private static Allocation allocationFor(Long occurrenceId) {
        return Allocation.builder().occurrenceId(occurrenceId).build();
    }

    @Test
    @DisplayName("findByOccurrenceIdIn: 70.000 ids se consultan en 7 tandas de 10.000 y se combinan los resultados")
    void findByOccurrenceIdInWith70000IdsQueriesInSevenChunks() {
        List<Integer> batchSizes = new ArrayList<>();
        when(repository.findChunkByOccurrenceIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            return batch.stream().filter(id -> id % 2 == 0).map(AllocationRepositoryTest::allocationFor).toList();
        });
        List<Long> ids = LongStream.rangeClosed(1, 70_000).boxed().toList();

        List<Allocation> result = repository.findByOccurrenceIdIn(ids);

        verify(repository, times(7)).findChunkByOccurrenceIdIn(anyCollection());
        assertThat(batchSizes).containsOnly(10_000).hasSize(7);
        assertThat(result).hasSize(35_000);
        assertThat(result).extracting(Allocation::getOccurrenceId)
                .containsExactlyElementsOf(ids.stream().filter(id -> id % 2 == 0).toList());
    }

    @Test
    @DisplayName("findByOccurrenceIdIn: colección vacía no consulta la base")
    void findByOccurrenceIdInWithEmptyCollectionDoesNotQuery() {
        List<Allocation> result = repository.findByOccurrenceIdIn(List.of());

        assertThat(result).isEmpty();
        verify(repository, times(0)).findChunkByOccurrenceIdIn(anyCollection());
    }
}
