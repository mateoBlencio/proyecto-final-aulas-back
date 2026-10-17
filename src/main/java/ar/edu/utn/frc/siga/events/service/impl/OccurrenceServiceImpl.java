package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OccurrenceServiceImpl implements OccurrenceService {

    private static final String DEPRECATED_RELEASE_NOTE = "Liberada desde endpoint deprecado";

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
    public Map<Long, LocalDate> findDatesByIds(Collection<Long> occurrenceIds) {
        return occurrenceRepository.findDatesByIdIn(occurrenceIds).stream()
                .collect(Collectors.toMap(OccurrenceRepository.OccurrenceDate::getId,
                        OccurrenceRepository.OccurrenceDate::getDate));
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
    @Transactional
    @AuditOperation("Alta de ocurrencias simultáneas")
    public List<Long> createSimultaneous(Long occurrenceId, int count) {
        Occurrence principal = Finder.orThrow(occurrenceRepository::findById, occurrenceId, "Occurrence");
        int nextSlot = occurrenceRepository.findByEvent_IdAndDate(principal.getEvent().getId(), principal.getDate())
                .stream()
                .mapToInt(Occurrence::getRoomSlot)
                .max()
                .orElse(1) + 1;

        List<Occurrence> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(Occurrence.builder()
                    .event(principal.getEvent())
                    .date(principal.getDate())
                    .status(OccurrenceStatus.NEEDS_ROOM)
                    .roomSlot(nextSlot + i)
                    .mirrorOfOccurrenceId(principal.getId())
                    .build());
        }
        return occurrenceRepository.saveAll(created).stream().map(Occurrence::getId).toList();
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
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> findMirrors(Collection<Long> principalOccurrenceIds) {
        if (principalOccurrenceIds.isEmpty()) {
            return List.of();
        }
        return occurrenceRepository.findByMirrorOfOccurrenceIdIn(principalOccurrenceIds).stream()
                .map(OccurrenceServiceImpl::toSlot).toList();
    }

    @Override
    @Transactional
    @AuditOperation("Liberación de ocurrencias")
    public void release(List<OccurrenceRelease> releases, ReleaseReason reason, String note) {
        if (reason == null) {
            throw new InvalidSelectionException("El motivo de liberación es obligatorio.");
        }
        if (reason == ReleaseReason.OTHER && (note == null || note.isBlank())) {
            throw new InvalidSelectionException("El motivo OTHER requiere una nota.");
        }
        Map<Long, Long> previousClassroomById = new HashMap<>();
        releases.forEach(r -> previousClassroomById.put(r.occurrenceId(), r.previousClassroomId()));
        List<Occurrence> occurrences = loadAll(previousClassroomById.keySet());
        occurrences.forEach(eventScheduleValidator::validateNotPast);
        for (Occurrence occurrence : occurrences) {
            occurrence.setStatus(OccurrenceStatus.ROOM_RELEASED);
            occurrence.setReleaseReason(reason);
            occurrence.setReleaseNote(note);
            Long previousClassroomId = previousClassroomById.get(occurrence.getId());
            if (previousClassroomId != null) {
                occurrence.setPreviousClassroomId(previousClassroomId);
            }
        }
    }

    @Override
    @Transactional
    @AuditOperation("Pedido de aula para ocurrencias")
    public void requestRoom(Collection<Long> occurrenceIds) {
        List<Occurrence> occurrences = loadAll(new HashSet<>(occurrenceIds));
        occurrences.forEach(eventScheduleValidator::validateNotPast);
        for (Occurrence occurrence : occurrences) {
            occurrence.setStatus(OccurrenceStatus.NEEDS_ROOM);
            occurrence.setReleaseReason(null);
            occurrence.setReleaseNote(null);
        }
    }

    // OBSOLETO: lo reemplaza POST/DELETE /v1/allocations/releases (con motivo y alcance por edificio). Pendiente de revisión por Matías antes de borrar.
    // @Override
    // @Transactional
    // @AuditOperation("Liberación de ocurrencia")
    // public void release(Long occurrenceId) {
    //     release(List.of(new OccurrenceRelease(occurrenceId, null)), ReleaseReason.OTHER, DEPRECATED_RELEASE_NOTE);
    //     eventPublisher.publishEvent(new OccurrenceVacated(occurrenceId, AuditOperations.currentOperationId()));
    // }

    private List<Occurrence> loadAll(Set<Long> ids) {
        List<Occurrence> occurrences = occurrenceRepository.findAllById(ids);
        if (occurrences.size() != ids.size()) {
            Set<Long> found = occurrences.stream().map(Occurrence::getId).collect(Collectors.toSet());
            Long missing = ids.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw ResourceNotFoundException.of("Occurrence", missing);
        }
        return occurrences;
    }

    private static OccurrenceSlotDto toSlot(Occurrence occurrence) {
        return new OccurrenceSlotDto(
                occurrence.getId(),
                occurrence.getEvent().getId(),
                occurrence.getDate(),
                occurrence.startTime(),
                occurrence.endTime(),
                occurrence.getStatus(),
                occurrence.getEvent().getEnrolled(),
                occurrence.getRoomSlot(),
                occurrence.getReleaseReason(),
                occurrence.getReleaseNote(),
                occurrence.getPreviousClassroomId(),
                occurrence.getMirrorOfOccurrenceId());
    }
}
