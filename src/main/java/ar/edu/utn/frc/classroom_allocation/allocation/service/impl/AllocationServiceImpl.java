package ar.edu.utn.frc.classroom_allocation.allocation.service.impl;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AcademicEventNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AllocationDomainException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AllocationNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.OccurrenceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.mapper.AllocationMapper;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AllocationService;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.space.repository.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ClassroomRepository classroomRepository;
    private final AllocationMapper mapper;

    @Override
    @Transactional
    public AllocationResponseDto assign(Long occurrenceId, AssignOccurrenceRequestDto dto) {
        log.debug("Assigning occurrence={} to classroom={}", occurrenceId, dto.classroomId());

        Occurrence occurrence = findOccurrence(occurrenceId);
        validateNotPast(occurrence);

        if (allocationRepository.findByOccurrence_Id(occurrenceId).isPresent()) {
            throw new AllocationDomainException(
                    "Occurrence " + occurrenceId + " already has an allocation. Use PUT /allocations/{id} to reassign.");
        }

        Classroom classroom = findClassroom(dto.classroomId());
        Allocation saved = allocationRepository.save(Allocation.builder()
                .occurrence(occurrence)
                .classroom(classroom)
                .assignedBy(dto.assignedBy())
                .createdAt(LocalDateTime.now())
                .observation(dto.observation())
                .build());

        log.info("Allocation created: id={}, occurrenceId={}, classroomId={}", saved.getId(), occurrenceId, dto.classroomId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AllocationResponseDto reassign(Long allocationId, AssignOccurrenceRequestDto dto) {
        log.debug("Reassigning allocation={} to classroom={}", allocationId, dto.classroomId());

        Allocation allocation = findAllocation(allocationId);
        validateNotPast(allocation.getOccurrence());

        allocation.setClassroom(findClassroom(dto.classroomId()));
        allocation.setAssignedBy(dto.assignedBy());
        allocation.setObservation(dto.observation());

        Allocation saved = allocationRepository.save(allocation);
        log.info("Allocation reassigned: id={}, classroomId={}", allocationId, dto.classroomId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public void cancel(Long allocationId) {
        log.debug("Cancelling allocation={}", allocationId);

        Allocation allocation = findAllocation(allocationId);
        validateNotPast(allocation.getOccurrence());

        allocationRepository.delete(allocation);
        log.info("Allocation cancelled: id={}", allocationId);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> assignFromDate(AssignFromDateRequestDto dto) {
        log.debug("assignFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> new AcademicEventNotFoundException(dto.recurringEventId()));

        if (!(event instanceof RecurringEvent)) {
            throw new AllocationDomainException("assignFromDate is only supported for recurring events");
        }

        Classroom classroom = findClassroom(dto.classroomId());
        LocalDate effectiveFrom = dto.fromDate().isBefore(LocalDate.now()) ? LocalDate.now() : dto.fromDate();

        List<Occurrence> occurrences = occurrenceRepository
                .findByEvent_IdAndDateGreaterThanEqual(dto.recurringEventId(), effectiveFrom);

        List<AllocationResponseDto> results = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (occurrence.isPast()) continue;

            Allocation allocation = allocationRepository.findByOccurrence_Id(occurrence.getId())
                    .map(existing -> {
                        existing.setClassroom(classroom);
                        existing.setAssignedBy(dto.assignedBy());
                        existing.setObservation(dto.observation());
                        return existing;
                    })
                    .orElseGet(() -> Allocation.builder()
                            .occurrence(occurrence)
                            .classroom(classroom)
                            .assignedBy(dto.assignedBy())
                            .createdAt(LocalDateTime.now())
                            .observation(dto.observation())
                            .build());

            results.add(mapper.toDto(allocationRepository.save(allocation)));
        }

        log.info("assignFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    private Occurrence findOccurrence(Long id) {
        return occurrenceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Occurrence not found: id={}", id);
                    return new OccurrenceNotFoundException(id);
                });
    }

    private Allocation findAllocation(Long id) {
        return allocationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Allocation not found: id={}", id);
                    return new AllocationNotFoundException(id);
                });
    }

    private Classroom findClassroom(Integer id) {
        return classroomRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("Classroom not found: id={}", id);
                    return new AllocationDomainException("Classroom not found with id: " + id);
                });
    }

    private void validateNotPast(Occurrence occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationDomainException(
                    "Cannot modify allocation: occurrence on " + occurrence.getDate() + " has already taken place.");
        }
    }
}
