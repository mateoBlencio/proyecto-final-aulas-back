package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    List<Occurrence> findByEvent_Id(Long eventId);

    List<Occurrence> findByEvent_IdAndDateGreaterThanEqual(Long eventId, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from, LocalDate to);
}
