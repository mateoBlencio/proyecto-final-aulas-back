package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long>, AllocationRepositoryCustom {

    Optional<Allocation> findByOccurrenceId(Long occurrenceId);
}
