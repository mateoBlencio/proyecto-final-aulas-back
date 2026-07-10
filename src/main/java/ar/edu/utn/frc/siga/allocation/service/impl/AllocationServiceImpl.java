package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationMapper;
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
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final AllocationRepository allocationRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final AcademicEventRepository eventRepository;
    private final ClassroomService classroomService;
    private final AllocationMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public AllocationResponseDto findById(Long allocationId) {
        Allocation allocation = allocationRepository.findByIdEager(allocationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Allocation", allocationId));
        return mapper.toDto(allocation);
    }

    @Override
    @Transactional
    public AllocationResponseDto assignManually(Long occurrenceId, AllocateOccurrenceRequestDto dto) {
        log.debug("Assigning occurrence={} to classroom={}", occurrenceId, dto.classroomId());

        Occurrence occurrence = findOccurrence(occurrenceId);
        validateNotPast(occurrence);
        validateAssignable(occurrence);

        if (allocationRepository.findByOccurrence_Id(occurrenceId).isPresent()) {
            throw new AllocationConflictException(
                    "Occurrence " + occurrenceId + " already has an allocation. Use PUT /allocations/{id} to reassign.");
        }

        Classroom classroom = findClassroom(dto.classroomId());
        Allocation saved = allocationRepository.save(Allocation.builder()
                .occurrence(occurrence)
                .classroom(classroom)
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .observation(dto.observation())
                .build());

        occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        occurrenceRepository.save(occurrence);

        log.info("Allocation created: id={}, occurrenceId={}, classroomId={}", saved.getId(), occurrenceId, dto.classroomId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AllocationResponseDto reassign(Long allocationId, AllocateOccurrenceRequestDto dto) {
        log.debug("Reassigning allocation={} to classroom={}", allocationId, dto.classroomId());

        Allocation allocation = findAllocation(allocationId);
        validateNotPast(allocation.getOccurrence());

        allocation.setClassroom(findClassroom(dto.classroomId()));
        allocation.setSource(AllocationSource.MANUAL);
        allocation.setObservation(dto.observation());

        Allocation saved = allocationRepository.save(allocation);
        log.info("Allocation reassigned: id={}, classroomId={}", allocationId, dto.classroomId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> batchReassign(BatchReassignRequestDto dto) {
        log.debug("batchReassign: moves={}", dto.moves().size());
        List<AllocationResponseDto> results = new ArrayList<>();
        for (BatchReassignRequestDto.MoveDto move : dto.moves()) {
            Allocation allocation = findAllocation(move.allocationId());
            validateNotPast(allocation.getOccurrence());
            allocation.setClassroom(findClassroom(move.classroomId()));
            allocation.setSource(AllocationSource.MANUAL);
            results.add(mapper.toDto(allocationRepository.save(allocation)));
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

        Classroom classroom = findClassroom(dto.classroomId());
        LocalDate effectiveFrom = dto.fromDate().isBefore(LocalDate.now()) ? LocalDate.now() : dto.fromDate();

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), effectiveFrom);

        validateNoOverlap(occurrences, classroom, event);

        List<AllocationResponseDto> results = allocateToOccurrences(
                occurrences, classroom, dto.observation(), AllocationSource.MANUAL, true);

        log.info("assignManuallyFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    /**
     * Verifica que todas las ocurrencias objetivo puedan asignarse al aula sin solapar
     * con asignaciones existentes de OTROS eventos. Si alguna choca, corta con 409 y
     * el detalle de cuáles.
     */
    private void validateNoOverlap(List<Occurrence> targets, Classroom classroom, AcademicEvent event) {
        List<Occurrence> future = targets.stream().filter(o -> !o.isPast()).toList();
        if (future.isEmpty()) return;

        LocalDate min = future.stream().map(Occurrence::getDate).min(java.util.Comparator.naturalOrder()).orElseThrow();
        LocalDate max = future.stream().map(Occurrence::getDate).max(java.util.Comparator.naturalOrder()).orElseThrow();
        java.util.Map<LocalDate, Occurrence> targetByDate = future.stream()
                .collect(java.util.stream.Collectors.toMap(Occurrence::getDate, o -> o, (a, b) -> a));

        java.time.LocalTime start = event.getStartTime();
        java.time.LocalTime end = event.endTime();

        List<ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (Allocation existing : allocationRepository.findOccupancyBetween(min, max, OccurrenceStatus.ASSIGNED)) {
            if (!existing.getClassroom().getId().equals(classroom.getId())) continue;
            AcademicEvent occupant = existing.getOccurrence().getEvent();
            if (occupant.getId().equals(event.getId())) continue; // sus propias asignaciones se reemplazan
            Occurrence target = targetByDate.get(existing.getOccurrence().getDate());
            if (target == null) continue;
            if (start.isBefore(occupant.endTime()) && occupant.getStartTime().isBefore(end)) {
                conflicts.add(new ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto(
                        target.getId(), target.getDate(), start, end,
                        classroom.getId(), occupant.getId(), existing.getId()));
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ReassignConflictException(conflicts);
        }
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

        Classroom classroom = findClassroom(dto.classroomId());

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), dto.fromDate());

        List<AllocationResponseDto> results = allocateToOccurrences(
                occurrences, classroom, dto.observation(), AllocationSource.IMPORTED, false);

        log.info("importAssignmentsFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    private List<AllocationResponseDto> allocateToOccurrences(
            List<Occurrence> occurrences, Classroom classroom, String observation,
            AllocationSource source, boolean skipPast) {
        List<AllocationResponseDto> results = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (skipPast && occurrence.isPast()) continue;
            if (!isAssignable(occurrence)) continue;

            Allocation allocation = allocationRepository.findByOccurrence_Id(occurrence.getId())
                    .map(existing -> {
                        existing.setClassroom(classroom);
                        existing.setSource(source);
                        existing.setObservation(observation);
                        return existing;
                    })
                    .orElseGet(() -> Allocation.builder()
                            .occurrence(occurrence)
                            .classroom(classroom)
                            .source(source)
                            .createdAt(LocalDateTime.now())
                            .observation(observation)
                            .build());

            results.add(mapper.toDto(allocationRepository.save(allocation)));

            occurrence.setStatus(OccurrenceStatus.ASSIGNED);
            occurrenceRepository.save(occurrence);
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByDate(LocalDate date) {
        log.debug("findByDate: date={}", date);
        return allocationRepository.findByDateEager(date)
                .stream()
                .map(mapper::toDto)
                .toList();
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

    private Classroom findClassroom(Integer id) {
        try {
            return classroomService.requireById(id);
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
