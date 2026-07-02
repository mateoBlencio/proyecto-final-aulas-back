package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSummaryDto;
import ar.edu.utn.frc.siga.allocation.exception.AcademicEventNotFoundException;
import ar.edu.utn.frc.siga.allocation.exception.AllocationDomainException;
import ar.edu.utn.frc.siga.allocation.exception.AllocationNotFoundException;
import ar.edu.utn.frc.siga.allocation.exception.OccurrenceNotFoundException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationMapper;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
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
                .orElseThrow(() -> new AllocationNotFoundException(allocationId));
        return mapper.toDto(allocation);
    }

    @Override
    @Transactional
    public AllocationResponseDto assign(Long occurrenceId, AllocateOccurrenceRequestDto dto) {
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
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .observation(dto.observation())
                .build());

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
    public void cancel(Long allocationId) {
        log.debug("Cancelling allocation={}", allocationId);

        Allocation allocation = findAllocation(allocationId);
        validateNotPast(allocation.getOccurrence());

        allocationRepository.delete(allocation);
        log.info("Allocation cancelled: id={}", allocationId);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> assignFromDate(AllocateFromDateRequestDto dto) {
        log.debug("assignFromDate: event={}, fromDate={}, classroom={}", dto.recurringEventId(), dto.fromDate(), dto.classroomId());

        AcademicEvent event = eventRepository.findById(dto.recurringEventId())
                .orElseThrow(() -> new AcademicEventNotFoundException(dto.recurringEventId()));

        if (!(Hibernate.unproxy(event) instanceof RecurringEvent)) {
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
                        existing.setSource(AllocationSource.MANUAL);
                        existing.setObservation(dto.observation());
                        return existing;
                    })
                    .orElseGet(() -> Allocation.builder()
                            .occurrence(occurrence)
                            .classroom(classroom)
                            .source(AllocationSource.MANUAL)
                            .createdAt(LocalDateTime.now())
                            .observation(dto.observation())
                            .build());

            results.add(mapper.toDto(allocationRepository.save(allocation)));
        }

        log.info("assignFromDate complete: event={}, fromDate={}, allocated={}", dto.recurringEventId(), dto.fromDate(), results.size());
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationSummaryDto> findByDate(LocalDate date) {
        log.debug("findByDate: date={}", date);
        return allocationRepository.findByDateEager(date)
                .stream()
                .map(mapper::toSummaryDto)
                .toList();
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
        try {
            return classroomService.requireById(id);
        } catch (ResourceNotFoundException ex) {
            log.warn("Classroom not found: id={}", id);
            throw new AllocationDomainException("Classroom not found with id: " + id);
        }
    }

    private void validateNotPast(Occurrence occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationDomainException(
                    "Cannot modify allocation: occurrence on " + occurrence.getDate() + " has already taken place.");
        }
    }
}
