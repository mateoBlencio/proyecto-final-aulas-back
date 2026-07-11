package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    List<Occurrence> findByEvent_Id(Long eventId);

    List<Occurrence> findByEvent_IdInAndStatus(Collection<Long> eventIds, OccurrenceStatus status);

    /**
     * Occurrences futuras de un conjunto de eventos en alguno de los estados dados. Se usa
     * para el auto-preview con re-resolución: incluir ASSIGNED (además de SCHEDULED) trae
     * las fechas de eventos ya asignados que el usuario quiere re-resolver; el filtro de
     * fecha evita re-resolver clases ya dictadas.
     */
    List<Occurrence> findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
            Collection<Long> eventIds, Collection<OccurrenceStatus> statuses, LocalDate date);

    List<Occurrence> findByEvent_IdAndDateGreaterThanEqual(Long eventId, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from, LocalDate to);
}
