package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByOccurrenceId(Long occurrenceId);

    List<Allocation> findByOccurrenceIdIn(Collection<Long> occurrenceIds);
}
