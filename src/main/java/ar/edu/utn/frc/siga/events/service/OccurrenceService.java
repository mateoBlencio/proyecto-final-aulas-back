package ar.edu.utn.frc.siga.events.service;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface OccurrenceService {

    OccurrenceSlotDto findSlot(Long occurrenceId);

    List<OccurrenceSlotDto> findSlots(Collection<Long> occurrenceIds);

    List<OccurrenceSlotDto> findSlotsByEvent(Long eventId, LocalDate from);

    /**
     * Crea {@code count} ocurrencias simultáneas a la de {@code occurrenceId}: mismo evento, misma
     * fecha, {@code roomSlot} correlativo a partir del máximo existente y {@code mirrorOfOccurrenceId}
     * apuntando a la principal. Es el único punto donde se crean ocurrencias simultáneas.
     */
    List<Long> createSimultaneous(Long occurrenceId, int count);

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from);

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds);

    List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsBetween(LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsByDate(LocalDate date);

    boolean existsOccurrence(Long occurrenceId);

    void release(Long occurrenceId);

    void requestRoom(Long occurrenceId);
}
