package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationRepositoryCustomImpl")
class AllocationRepositoryCustomImplTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<Allocation> query;

    private static Allocation allocationFor(Long occurrenceId) {
        return Allocation.builder().occurrenceId(occurrenceId).build();
    }

    @Test
    @DisplayName("findByOccurrenceIdIn: 65.000 ids se consultan en 7 tandas de hasta 10.000 y se concatenan los resultados")
    void findByOccurrenceIdInWith65000IdsQueriesInSevenChunks() {
        List<Integer> batchSizes = new ArrayList<>();
        List<Collection<Long>> batches = new ArrayList<>();
        when(entityManager.createQuery(any(String.class), eq(Allocation.class))).thenReturn(query);
        when(query.setParameter(eq("ids"), any())).thenAnswer(invocation -> {
            Collection<Long> batch = invocation.getArgument(1);
            batchSizes.add(batch.size());
            batches.add(batch);
            return query;
        });
        when(query.getResultList()).thenAnswer(invocation ->
                batches.getLast().stream().map(AllocationRepositoryCustomImplTest::allocationFor).toList());
        List<Long> ids = LongStream.rangeClosed(1, 65_000).boxed().toList();

        List<Allocation> result = new AllocationRepositoryCustomImpl(entityManager).findByOccurrenceIdIn(ids);

        verify(query, times(7)).getResultList();
        assertThat(batchSizes).containsExactly(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 5_000);
        assertThat(result).extracting(Allocation::getOccurrenceId).containsExactlyElementsOf(ids);
    }

    @Test
    @DisplayName("findByOccurrenceIdIn: colección vacía no consulta la base")
    void findByOccurrenceIdInWithEmptyCollectionDoesNotQuery() {
        List<Allocation> result = new AllocationRepositoryCustomImpl(entityManager).findByOccurrenceIdIn(List.of());

        assertThat(result).isEmpty();
        verify(entityManager, never()).createQuery(any(String.class), eq(Allocation.class));
    }
}
