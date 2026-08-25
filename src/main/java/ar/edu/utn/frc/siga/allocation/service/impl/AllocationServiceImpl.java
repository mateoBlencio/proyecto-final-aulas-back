package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;
    private final AllocationComposer composer;
    private final AllocationValidator validator;
    private final AllocationTargetResolver targetResolver;
    private final AllocationWriter writer;

    @Override
    @Transactional(readOnly = true)
    public AllocationResponseDto findById(Long allocationId) {
        return composer.compose(findAllocation(allocationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByDate(LocalDate date) {
        List<Long> occurrenceIds = occurrenceService.findSlotsByDate(date).stream()
                .map(OccurrenceSlotDto::occurrenceId).toList();
        return composer.composeAll(allocationRepository.findByOccurrenceIdIn(occurrenceIds));
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> allocate(AllocationCommand command) {
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolveAndValidate(command);
        List<Allocation> saved = writer.create(classroomByOccurrence, command.observation(), command.source());
        log.info("Asignación creada: source={}, count={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> reallocate(AllocationCommand command) {
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolveAndValidate(command);
        List<Allocation> saved = writer.upsert(classroomByOccurrence, command.observation(), command.source());
        log.info("Asignación actualizada: source={}, count={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    public List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command) {
        List<OccurrenceSlotDto> occurrences = targetResolver.resolveAll(command.targets(), null);
        occurrences.forEach(validator::validateNotPast);

        List<AllocationWriter.DeallocatedOccurrence> deallocated = writer.delete(occurrences);
        log.info("Asignación liberada: count={}", deallocated.size());
        return deallocated.stream()
                .map(d -> new DeallocatedOccurrenceDto(d.occurrenceId(), d.classroomId()))
                .toList();
    }

    private Map<OccurrenceSlotDto, Long> resolveAndValidate(AllocationCommand command) {
        LocalDate clampFrom = command.source() == AllocationSource.IMPORTED ? null : LocalDate.now();
        Map<OccurrenceSlotDto, Long> classroomByOccurrence =
                targetResolver.resolveClassroomByOccurrence(command.items(), clampFrom);

        if (classroomByOccurrence.isEmpty()) {
            return classroomByOccurrence;
        }

        Set<Long> classroomIds = Set.copyOf(classroomByOccurrence.values());
        validator.validateClassroomsAvailable(classroomIds);

        if (command.source() == AllocationSource.MANUAL) {
            List<AllocationCandidate> candidates = classroomByOccurrence.entrySet().stream()
                    .map(e -> new AllocationCandidate(e.getKey(), e.getValue()))
                    .toList();
            validator.validateNoOverlap(candidates);
        }

        return classroomByOccurrence;
    }

    private Allocation findAllocation(Long id) {
        return Finder.orThrow(allocationRepository::findById, id, "Allocation");
    }
}
