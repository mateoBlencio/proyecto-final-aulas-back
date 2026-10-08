package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.util.Chunks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByOccurrenceId(Long occurrenceId);

    /**
     * Raw query: do not call directly, it exceeds PostgreSQL's parameter limit with large collections.
     * Use {@link #findByOccurrenceIdIn(Collection)}.
     */
    List<Allocation> findChunkByOccurrenceIdIn(Collection<Long> occurrenceIds);

    default List<Allocation> findByOccurrenceIdIn(Collection<Long> occurrenceIds) {
        return Chunks.query(occurrenceIds, 10_000, this::findChunkByOccurrenceIdIn);
    }
}
