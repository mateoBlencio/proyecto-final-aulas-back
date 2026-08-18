package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OccurrenceServiceImpl implements OccurrenceService {

    private final OccurrenceRepository occurrenceRepository;
    private final EventScheduleValidator eventScheduleValidator;
    private final ApplicationEventPublisher eventPublisher;

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
        List<Occurrence> occurrences = from == null
                ? occurrenceRepository.findByEvent_Id(eventId)
                : occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eventId, from);
        return occurrences.stream().map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds, LocalDate from) {
        return occurrenceRepository.findByEvent_IdInAndDateGreaterThanEqual(eventIds, from).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByEvents(Collection<Long> eventIds) {
        return occurrenceRepository.findByEvent_IdIn(eventIds).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsByStatusBetween(OccurrenceStatus status, LocalDate from, LocalDate to) {
        return occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(status, from, to).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findSlotsBetween(LocalDate from, LocalDate to) {
        return occurrenceRepository.findByDateBetween(from, to).stream()
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
    public void release(Long occurrenceId) {
        Occurrence occurrence = Finder.orThrow(occurrenceRepository::findById, occurrenceId, "Occurrence");
        eventScheduleValidator.validateNotPast(occurrence);
        occurrence.setStatus(OccurrenceStatus.ROOM_RELEASED);
        eventPublisher.publishEvent(new OccurrenceVacated(occurrenceId));
    }

    @Override
    @Transactional
    public void requestRoom(Long occurrenceId) {
        Occurrence occurrence = Finder.orThrow(occurrenceRepository::findById, occurrenceId, "Occurrence");
        eventScheduleValidator.validateNotPast(occurrence);
        occurrence.setStatus(OccurrenceStatus.NEEDS_ROOM);
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
