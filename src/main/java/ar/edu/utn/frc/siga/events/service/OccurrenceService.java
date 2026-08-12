package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Acceso de solo lectura a la franja de las occurrences: lo único que {@code allocation}
 * necesita del evento para validar solapamiento/capacidad, sin depender de la entidad
 * {@code Occurrence}. Tener aula NO se resuelve acá (es {@code allocation}, ver {@code Allocation});
 * este servicio solo expone si la occurrence {@code NEEDS_ROOM}/{@code ROOM_RELEASED}.
 */
@NamedInterface("api")
public interface OccurrenceService {

    /** 404 si no existe. */
    OccurrenceSlotDto findSlot(Long occurrenceId);

    /** Batch por ids — evita N+1. Ids inexistentes simplemente no aparecen en el resultado. */
    List<OccurrenceSlotDto> findSlots(Collection<Long> occurrenceIds);

    /** Occurrences de un evento desde una fecha (inclusive); {@code null} trae todas (incluidas las pasadas). */
    List<OccurrenceSlotDto> findSlotsByEvent(Long eventId, LocalDate from);

    /** Igual que {@link #findSlotsByEvent} pero para varios eventos a la vez (sin N+1). */
    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from);

    /** Todas las occurrences de varios eventos, sin filtro de fecha (p. ej. la única occurrence de cada evento único). */
    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds);

    /** Occurrences en un estado dado, entre dos fechas (inclusive). */
    List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to);

    /** Todas las occurrences (cualquier estado) entre dos fechas (inclusive). */
    List<OccurrenceSlotDto> findSlotsBetween(LocalDate from, LocalDate to);

    /** Todas las occurrences (cualquier estado) de una fecha puntual. */
    List<OccurrenceSlotDto> findSlotsByDate(LocalDate date);

    boolean existsOccurrence(Long occurrenceId);

    /**
     * NEEDS_ROOM → ROOM_RELEASED. Publica {@link ar.edu.utn.frc.siga.events.model.OccurrenceVacated}
     * en la misma transacción (commit-time, at-least-once vía Spring Modulith). Rechaza si la
     * occurrence ya ocurrió.
     */
    void release(Long occurrenceId);

    /**
     * ROOM_RELEASED → NEEDS_ROOM. No publica evento: no hay Allocation que tocar (la ocurrencia
     * nunca tuvo aula asignada mientras estaba liberada, o si la tuvo ya fue borrada al liberar).
     * Rechaza si la occurrence ya ocurrió.
     */
    void requestRoom(Long occurrenceId);
}
