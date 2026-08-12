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

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from);

    List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds);

    List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsBetween(LocalDate from, LocalDate to);

    List<OccurrenceSlotDto> findSlotsByDate(LocalDate date);

    boolean existsOccurrence(Long occurrenceId);

    void release(Long occurrenceId);

    void requestRoom(Long occurrenceId);
}
