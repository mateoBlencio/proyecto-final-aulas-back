package ar.edu.utn.frc.classroom_allocation.allocation.repository;

import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByOccurrence_Id(Long occurrenceId);

    List<Allocation> findByOccurrence_Event_IdAndOccurrence_DateGreaterThanEqual(Long eventId, LocalDate date);
}
