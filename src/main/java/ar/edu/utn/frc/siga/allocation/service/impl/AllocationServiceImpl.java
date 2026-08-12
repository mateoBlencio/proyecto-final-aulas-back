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

/**
 * Implementación de los tres verbos: resuelve el/los {@code AllocationTarget} de cada item
 * contra el estado actual de BD ({@link AllocationTargetResolver}), valida (aulas
 * existentes/disponibles siempre; solapamiento solo si {@code source == MANUAL}, ver
 * {@link AllocationValidator}) y delega la escritura atómica al {@link AllocationWriter}.
 */
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
        log.debug("findByDate: date={}", date);
        List<Long> occurrenceIds = occurrenceService.findSlotsByDate(date).stream()
                .map(OccurrenceSlotDto::occurrenceId).toList();
        return composer.composeAll(allocationRepository.findByOccurrenceIdIn(occurrenceIds));
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> allocate(AllocationCommand command) {
        log.debug("allocate: source={}, items={}", command.source(), command.items().size());
        Map<OccurrenceSlotDto, Integer> classroomByOccurrence = resolveAndValidate(command);
        List<Allocation> saved = writer.create(classroomByOccurrence, command.observation(), command.source());
        log.info("allocate completo: source={}, allocated={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    public List<AllocationResponseDto> reallocate(AllocationCommand command) {
        log.debug("reallocate: source={}, items={}", command.source(), command.items().size());
        Map<OccurrenceSlotDto, Integer> classroomByOccurrence = resolveAndValidate(command);
        List<Allocation> saved = writer.upsert(classroomByOccurrence, command.observation(), command.source());
        log.info("reallocate completo: source={}, allocated={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    public List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command) {
        log.debug("deallocate: targets={}", command.targets().size());
        List<OccurrenceSlotDto> occurrences = targetResolver.resolveAll(command.targets(), null);
        occurrences.forEach(validator::validateNotPast);

        List<AllocationWriter.DeallocatedOccurrence> deallocated = writer.delete(occurrences);
        log.info("deallocate completo: freed={}", deallocated.size());
        return deallocated.stream()
                .map(d -> new DeallocatedOccurrenceDto(d.occurrenceId(), d.classroomId()))
                .toList();
    }

    /**
     * Clampea por source ({@code IMPORTED} incluye pasadas, el resto no —
     * {@link AllocationTargetResolver}), resuelve el aula de cada occurrence del lote, valida
     * aulas existentes/disponibles y —solo para {@code MANUAL}— que nada choque contra la
     * ocupación firme de BD ni entre sí. Un lote que no resuelve a ninguna occurrence
     * aplicable (evento sin ocurrencias futuras, todas canceladas/suspendidas) es un no-op:
     * mismo criterio que ya tenía {@code AllocationWriter} al saltear no-aplicables.
     */
    private Map<OccurrenceSlotDto, Integer> resolveAndValidate(AllocationCommand command) {
        LocalDate clampFrom = command.source() == AllocationSource.IMPORTED ? null : LocalDate.now();
        Map<OccurrenceSlotDto, Integer> classroomByOccurrence =
                targetResolver.resolveClassroomByOccurrence(command.items(), clampFrom);

        if (classroomByOccurrence.isEmpty()) {
            return classroomByOccurrence;
        }

        Set<Integer> classroomIds = Set.copyOf(classroomByOccurrence.values());
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
