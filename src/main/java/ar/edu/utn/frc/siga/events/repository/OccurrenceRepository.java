package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    List<Occurrence> findByEvent_Id(Long eventId);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdIn(Collection<Long> eventIds);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
            Collection<Long> eventIds, Collection<OccurrenceStatus> statuses, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdAndDateGreaterThanEqual(Long eventId, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdInAndDateGreaterThanEqual(Collection<Long> eventIds, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByDate(LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByDateBetween(LocalDate from, LocalDate to);
}
