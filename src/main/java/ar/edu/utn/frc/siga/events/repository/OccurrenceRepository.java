package ar.edu.utn.frc.siga.events.repository;

import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {

    interface OccurrenceDate {
        Long getId();

        LocalDate getDate();
    }

    /** Id and date only: does not initialize the event. */
    @Query("select o.id as id, o.date as date from Occurrence o where o.id in :ids")
    List<OccurrenceDate> findDatesByIdIn(Collection<Long> ids);

    @Query(value = """
            select x.id_ocurrencia from (
                select o.id_ocurrencia,
                       row_number() over (partition by o.id_evento_academico order by o.fecha, o.orden_aula) as rn
                from ocurrencia o
                where o.id_evento_academico in (:eventIds)
                  and o.fecha >= :from
                  and o.id_ocurrencia_principal is null
                  and o.fecha <> all (:excludedDates)
            ) x
            where x.rn <= :limit
            """, nativeQuery = true)
    List<Long> findNextIdsPerEvent(Collection<Long> eventIds, LocalDate from, LocalDate[] excludedDates, int limit);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByIdIn(Collection<Long> ids);

    List<Occurrence> findByEvent_Id(Long eventId);

    List<Occurrence> findByEvent_IdAndDate(Long eventId, LocalDate date);

    @EntityGraph(attributePaths = "event")
    List<Occurrence> findByMirrorOfOccurrenceIdIn(Collection<Long> principalIds);

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
