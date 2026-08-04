package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso a {@code Allocation} (asignación vigente de aula a una occurrence). */
@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByOccurrenceId(Long occurrenceId);

    /** Batch por ids de ocurrencia — evita N+1 al aplicar un confirm sobre muchas occurrences. */
    List<Allocation> findByOccurrenceIdIn(Collection<Long> occurrenceIds);
}
