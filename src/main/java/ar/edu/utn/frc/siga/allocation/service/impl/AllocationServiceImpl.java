package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final AllocationRepository allocationRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventRepository eventRepository;
    private final ClassroomService classroomService;
    private final AllocationComposer composer;

    @Override
    @Transactional(readOnly = true)
    public AllocationResponseDto findById(Long allocationId) {
        Allocation allocation = allocationRepository.findByIdEager(allocationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Allocation", allocationId));
        return composer.compose(allocation);
    }

    @Override
    @Transactional
    public AllocationResponseDto assignManually(Long occurrenceId, AllocateOccurrenceRequestDto dto) {
        log.debug("Assigning occurrence={} to classroom={}", occurrenceId, dto.classroomId());

        Occurrence occurrence = findOccurrence(occurrenceId);
        validateNotPast(occurrence);
        validateAssignable(occurrence);

        if (allocationRepository.findByOccurrence_Id(occurrenceId).isPresent()) {
            throw new AllocationConflictException("Occurrence " + occurrenceId + " already has an allocation.");
        }

        Integer classroomId = findClassroom(dto.classroomId());
        validateNoOverlap(List.of(new OverlapCandidate(occurrence, classroomId)));

        Allocation saved = allocationRepository.save(Allocation.builder()
                .occurrence(occurrence)
                .classroomId(classroomId)
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .observation(dto.observation())
                .build());

        occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        occurrenceRepository.save(occurrence);

        log.info("Allocation created: id={}, occurrenceId={}, classroomId={}", saved.getId(), occurrenceId, dto.classroomId());
        return composer.compose(saved);
    }

    @Override
    @Transactional
    public AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto) {
        log.debug("Reassigning allocation={} to classroom={}", allocationId, dto.classroomId());

        Allocation allocation = findAllocation(allocationId);
        validateNotPast(allocation.getOccurrence());

        Integer classroomId = findClassroom(dto.classroomId());
        validateNoOverlap(List.of(new OverlapCandidate(allocation.getOccurrence(), classroomId)));

        allocation.setClassroomId(classroomId);
        allocation.setSource(AllocationSource.MANUAL);
        allocation.setObservation(dto.observation());

        Allocation saved = allocationRepository.save(allocation);
        log.info("Allocation reassigned: id={}, classroomId={}", allocationId, dto.classroomId());
        return composer.compose(saved);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> batchReassign(BatchReassignRequestDto dto) {
        log.debug("batchReassign: moves={}", dto.moves().size());

        // Primero se resuelven y validan TODOS los moves (contra BD y entre sí); nada
        // se escribe hasta que el lote completo esté libre de solapamientos.
        List<Allocation> allocations = new ArrayList<>();
        List<OverlapCandidate> candidates = new ArrayList<>();
        for (BatchReassignRequestDto.MoveDto move : dto.moves()) {
            Allocation allocation = findAllocation(move.allocationId());
            validateNotPast(allocation.getOccurrence());
            Integer classroomId = findClassroom(move.classroomId());
            allocations.add(allocation);
            candidates.add(new OverlapCandidate(allocation.getOccurrence(), classroomId));
        }

        validateNoOverlap(candidates);

        List<AllocationResponseDto> results = new ArrayList<>();
        for (int i = 0; i < allocations.size(); i++) {
            Allocation allocation = allocations.get(i);
            allocation.setClassroomId(candidates.get(i).classroomId());
            allocation.setSource(AllocationSource.MANUAL);
            results.add(composer.compose(allocationRepository.save(allocation)));
        }
        log.info("batchReassign complete: moved={}", results.size());
        return results;
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> assignManuallyFromDate(AllocateFromDateRequestDto dto) {
        log.debug("assignManuallyFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", dto.recurringEventId()));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
            throw new AllocationConflictException("assignManuallyFromDate is only supported for recurring events");
        }

        Integer classroomId = findClassroom(dto.classroomId());
        LocalDate effectiveFrom = dto.fromDate().isBefore(LocalDate.now()) ? LocalDate.now() : dto.fromDate();

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), effectiveFrom);

        validateNoOverlap(occurrences.stream().map(o -> new OverlapCandidate(o, classroomId)).toList());

        List<AllocationResponseDto> results = allocateToOccurrences(
                occurrences, classroomId, dto.observation(), AllocationSource.MANUAL, true);

        log.info("assignManuallyFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> importAssignmentsFromDate(AllocateFromDateRequestDto dto) {
        log.debug("importAssignmentsFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicEvent", dto.recurringEventId()));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
            throw new AllocationConflictException("importAssignmentsFromDate is only supported for recurring events");
        }

        Integer classroomId = findClassroom(dto.classroomId());

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), dto.fromDate());

        List<AllocationResponseDto> results = allocateToOccurrences(
                occurrences, classroomId, dto.observation(), AllocationSource.IMPORTED, false);

        log.info("importAssignmentsFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    private List<AllocationResponseDto> allocateToOccurrences(
            List<Occurrence> occurrences, Integer classroomId, String observation,
            AllocationSource source, boolean skipPast) {
        List<Allocation> saved = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (skipPast && occurrence.isPast()) continue;
            if (!isAssignable(occurrence)) continue;

            Allocation allocation = allocationRepository.findByOccurrence_Id(occurrence.getId())
                    .map(existing -> {
                        existing.setClassroomId(classroomId);
                        existing.setSource(source);
                        existing.setObservation(observation);
                        return existing;
                    })
                    .orElseGet(() -> Allocation.builder()
                            .occurrence(occurrence)
                            .classroomId(classroomId)
                            .source(source)
                            .createdAt(LocalDateTime.now())
                            .observation(observation)
                            .build());

            saved.add(allocationRepository.save(allocation));

            occurrence.setStatus(OccurrenceStatus.ASSIGNED);
            occurrenceRepository.save(occurrence);
        }
        return composer.composeAll(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByDate(LocalDate date) {
        log.debug("findByDate: date={}", date);
        return composer.composeAll(allocationRepository.findByDateEager(date));
    }

    /** Ocurrencia a (re)asignar y el aula destino que se le quiere dar. */
    private record OverlapCandidate(Occurrence occurrence, Integer classroomId) {
    }

    /**
     * Verifica que ninguno de los candidatos (ocurrencia + aula destino) solape con
     * asignaciones ASSIGNED existentes ni entre sí. Las propias ocurrencias de los
     * candidatos se excluyen de la ocupación de BD (sus asignaciones actuales, si
     * existen, se están reemplazando/moviendo en esta misma operación). Si algo choca,
     * corta con 409 y el detalle de todos los conflictos encontrados; nada se escribe.
     */
    private void validateNoOverlap(List<OverlapCandidate> candidates) {
        List<OverlapCandidate> future = candidates.stream().filter(c -> !c.occurrence().isPast()).toList();
        if (future.isEmpty()) return;

        LocalDate min = future.stream().map(c -> c.occurrence().getDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = future.stream().map(c -> c.occurrence().getDate()).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> ownOccurrenceIds = future.stream().map(c -> c.occurrence().getId()).collect(Collectors.toSet());

        List<Allocation> occupancy = allocationRepository.findOccupancyBetween(min, max, OccurrenceStatus.ASSIGNED)
                .stream()
                .filter(a -> !ownOccurrenceIds.contains(a.getOccurrence().getId()))
                .toList();

        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(databaseConflicts(future, occupancy));
        conflicts.addAll(internalConflicts(future));

        if (!conflicts.isEmpty()) {
            throw new ReassignConflictException(conflicts);
        }
    }

    /** Conflictos de un candidato contra ocupación ASSIGNED firme de BD (ya sin lo propio). */
    private List<OccurrenceConflictDto> databaseConflicts(List<OverlapCandidate> candidates, List<Allocation> occupancy) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (OverlapCandidate candidate : candidates) {
            LocalTime start = candidate.occurrence().startTime();
            LocalTime end = candidate.occurrence().endTime();
            for (Allocation existing : occupancy) {
                if (!existing.getClassroomId().equals(candidate.classroomId())) continue;
                if (!existing.getOccurrence().getDate().equals(candidate.occurrence().getDate())) continue;
                AcademicEvent occupant = existing.getOccurrence().getEvent();
                if (!overlaps(start, end, occupant.getStartTime(), occupant.endTime())) continue;
                conflicts.add(new OccurrenceConflictDto(candidate.occurrence().getId(), candidate.occurrence().getDate(),
                        start, end, candidate.classroomId(), occupant.getId(), existing.getId()));
            }
        }
        return conflicts;
    }

    /**
     * Conflictos entre los propios candidatos: dos ocurrencias distintas cayendo en la
     * misma aula/fecha con franjas que se pisan (relevante para {@code batchReassign},
     * donde varios moves se validan juntos). Nada persiste todavía → no hay asignación
     * real involucrada en el choque, {@code conflictingAllocationId} va null.
     */
    private List<OccurrenceConflictDto> internalConflicts(List<OverlapCandidate> candidates) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            OverlapCandidate a = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                OverlapCandidate b = candidates.get(j);
                if (!a.classroomId().equals(b.classroomId())) continue;
                if (!a.occurrence().getDate().equals(b.occurrence().getDate())) continue;
                LocalTime aStart = a.occurrence().startTime();
                LocalTime aEnd = a.occurrence().endTime();
                LocalTime bStart = b.occurrence().startTime();
                LocalTime bEnd = b.occurrence().endTime();
                if (!overlaps(aStart, aEnd, bStart, bEnd)) continue;
                conflicts.add(new OccurrenceConflictDto(a.occurrence().getId(), a.occurrence().getDate(),
                        aStart, aEnd, a.classroomId(), b.occurrence().getEvent().getId(), null));
            }
        }
        return conflicts;
    }

    /** Barrido de franjas horarias: fin == inicio no es solapamiento. */
    private boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    private Occurrence findOccurrence(Long id) {
        return occurrenceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Occurrence not found: id={}", id);
                    return ResourceNotFoundException.of("Occurrence", id);
                });
    }

    private Allocation findAllocation(Long id) {
        return allocationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Allocation not found: id={}", id);
                    return ResourceNotFoundException.of("Allocation", id);
                });
    }

    /** Valida que el aula exista (404 de la fachada de space) y devuelve su ID plano. */
    private Integer findClassroom(Integer id) {
        try {
            classroomService.findById(id);
            return id;
        } catch (ResourceNotFoundException ex) {
            log.warn("Classroom not found: id={}", id);
            throw new AllocationConflictException("Classroom not found with id: " + id);
        }
    }

    private void validateNotPast(Occurrence occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationConflictException(
                    "Cannot modify allocation: occurrence on " + occurrence.getDate() + " has already taken place.");
        }
    }

    private void validateAssignable(Occurrence occurrence) {
        if (!isAssignable(occurrence)) {
            throw new AllocationConflictException(
                    "Cannot assign classroom: occurrence " + occurrence.getId() + " is " + occurrence.getStatus() + ".");
        }
    }

    private boolean isAssignable(Occurrence occurrence) {
        return occurrence.getStatus() != OccurrenceStatus.CANCELLED
                && occurrence.getStatus() != OccurrenceStatus.SUSPENDED;
    }
}
