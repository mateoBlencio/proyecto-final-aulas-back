package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.util.Chunks;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

// Spring Data requires the "Impl" suffix on the fragment interface name.
@RequiredArgsConstructor
class AllocationRepositoryCustomImpl implements AllocationRepositoryCustom {

    // Chunks keep each IN query under PostgreSQL's 65,535 parameter limit.
    static final int CHUNK_SIZE = 10_000;

    private final EntityManager entityManager;

    @Override
    public List<Allocation> findByOccurrenceIdIn(Collection<Long> occurrenceIds) {
        return Chunks.query(occurrenceIds, CHUNK_SIZE, chunk -> entityManager
                .createQuery("select a from Allocation a where a.occurrenceId in :ids", Allocation.class)
                .setParameter("ids", chunk)
                .getResultList());
    }
}
