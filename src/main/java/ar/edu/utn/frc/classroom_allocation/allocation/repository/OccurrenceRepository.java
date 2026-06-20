package ar.edu.utn.frc.classroom_allocation.allocation.repository;

import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    List<Occurrence> findByEvent_Id(Long eventId);

    Optional<Occurrence> findByEvent_IdAndDate(Long eventId, LocalDate date);

    List<Occurrence> findByEvent_IdAndDateGreaterThanEqual(Long eventId, LocalDate date);
}
