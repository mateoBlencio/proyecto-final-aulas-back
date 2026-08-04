package ar.edu.utn.frc.siga.allocation.events.repository;

import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Acceso a {@code Occurrence} (fechas concretas generadas por un {@code AcademicEvent}). */
@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    List<Occurrence> findByEvent_Id(Long eventId);

    /** Batch por ids de evento (sin N+1) — usado por el composer para resolver estado/aula de eventos únicos. */
    List<Occurrence> findByEvent_IdIn(Collection<Long> eventIds);

    /**
     * Occurrences futuras de un conjunto de eventos en alguno de los estados dados. Se usa
     * para el auto-preview con re-resolución: incluir ASSIGNED (además de SCHEDULED) trae
     * las fechas de eventos ya asignados que el usuario quiere re-resolver; el filtro de
     * fecha evita re-resolver clases ya dictadas.
     */
    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
            Collection<Long> eventIds, Collection<OccurrenceStatus> statuses, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdAndDateGreaterThanEqual(Long eventId, LocalDate date);

    /**
     * Igual que {@link #findByEvent_IdAndDateGreaterThanEqual} pero para varios eventos a
     * la vez: una sola query en vez de una por evento (import masivo desde Excel). El
     * caller filtra en memoria si cada evento tiene su propia fecha desde (acá se pasa la
     * más antigua de todas y se sobre-trae).
     */
    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByEvent_IdInAndDateGreaterThanEqual(Collection<Long> eventIds, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(
            OccurrenceStatus status, LocalDate from, LocalDate to);

    /** Todas las occurrences (cualquier estado) de una fecha puntual — usado por {@code allocation} para listar por fecha. */
    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByDate(LocalDate date);
}
