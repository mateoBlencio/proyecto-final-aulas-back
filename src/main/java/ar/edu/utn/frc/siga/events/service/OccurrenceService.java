package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Acceso de solo lectura (más el pase a ASSIGNED) a la franja de las occurrences: lo único
 * que {@code allocation} necesita del evento para validar solapamiento/capacidad, sin
 * depender de la entidad {@code Occurrence}.
 */
@NamedInterface("api")
public interface OccurrenceService {

    /** 404 si no existe. */
    OccurrenceSlotDto findSlot(Long occurrenceId);

    /** Batch por ids — evita N+1. Ids inexistentes simplemente no aparecen en el resultado. */
    List<OccurrenceSlotDto> findSlots(Collection<Long> occurrenceIds);

    /** Occurrences de un evento desde una fecha (inclusive). */
    List<OccurrenceSlotDto> findSlotsByEvent(Long eventId, LocalDate from);

    /** Igual que {@link #findSlotsByEvent} pero para varios eventos a la vez (sin N+1). */
    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from);

    /** Todas las occurrences de varios eventos, sin filtro de fecha (p. ej. la única occurrence de cada evento único). */
    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds);

    /** Occurrences de varios eventos, en alguno de los estados dados, desde una fecha. */
    List<OccurrenceSlotDto> findSlotsByEventsAndStatuses(
            Collection<Long> eventIds, Collection<OccurrenceStatus> statuses, LocalDate from);

    /** Occurrences en un estado dado, entre dos fechas (inclusive). */
    List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to);

    /** Todas las occurrences (cualquier estado) de una fecha puntual. */
    List<OccurrenceSlotDto> findSlotsByDate(LocalDate date);

    boolean existsOccurrence(Long occurrenceId);

    /** Pasa a ASSIGNED las occurrences indicadas (dirty checking, misma transacción del caller). */
    void markAssigned(Collection<Long> occurrenceIds);
}
