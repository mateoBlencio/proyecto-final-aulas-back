package ar.edu.utn.frc.siga.allocation.events.service.impl;

import ar.edu.utn.frc.siga.allocation.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OccurrenceServiceImpl implements OccurrenceService {

    private final OccurrenceRepository occurrenceRepository;

    @Override
    @Transactional(readOnly = true)
    public OccurrenceSlotDto findSlot(Long occurrenceId) {
        return toSlot(Finder.orThrow(occurrenceRepository::findById, occurrenceId, "Occurrence"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlots(Collection<Long> occurrenceIds) {
        return occurrenceRepository.findAllById(occurrenceIds).stream().map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByEvent(Long eventId, LocalDate from) {
        return occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eventId, from).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from) {
        return occurrenceRepository.findByEvent_IdInAndDateGreaterThanEqual(eventIds, from).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByEventsAndStatuses(
            Collection<Long> eventIds, Collection<OccurrenceStatus> statuses, LocalDate from) {
        return occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(eventIds, statuses, from)
                .stream().map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to) {
        return occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(status, from, to).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByDate(LocalDate date) {
        return occurrenceRepository.findByDate(date).stream().map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOccurrence(Long occurrenceId) {
        return occurrenceRepository.existsById(occurrenceId);
    }

    @Override
    @Transactional
    public void markAssigned(Collection<Long> occurrenceIds) {
        occurrenceRepository.findAllById(occurrenceIds)
                .forEach(occurrence -> occurrence.setStatus(OccurrenceStatus.ASSIGNED));
    }

    private static OccurrenceSlotDto toSlot(Occurrence occurrence) {
        return new OccurrenceSlotDto(
                occurrence.getId(),
                occurrence.getEvent().getId(),
                occurrence.getDate(),
                occurrence.startTime(),
                occurrence.endTime(),
                occurrence.getStatus(),
                occurrence.getEvent().getEnrolled());
    }
}
