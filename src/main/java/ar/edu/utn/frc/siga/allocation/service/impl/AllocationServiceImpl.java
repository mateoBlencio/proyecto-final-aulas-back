package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ClassroomService classroomService;
    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    @Transactional(readOnly = true)
    public AllocationResponseDto findById(Long allocationId) {
        AllocationResponseDto dto = composer.compose(findAllocation(allocationId));
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ);
        if (dto.classroom() != null && !scope.allows(dto.classroom().buildingId())) {
            throw ResourceNotFoundException.of("Allocation", allocationId);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByDate(LocalDate date) {
        List<Long> occurrenceIds = occurrenceService.findSlotsByDate(date).stream()
                .map(OccurrenceSlotDto::occurrenceId).toList();
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ);
        return composer.composeAll(allocationRepository.findByOccurrenceIdIn(occurrenceIds)).stream()
                .filter(dto -> dto.classroom() == null || scope.allows(dto.classroom().buildingId()))
                .toList();
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
        requireAccessToCurrentClassrooms(occurrences);

        List<AllocationWriter.DeallocatedOccurrence> deallocated = writer.delete(occurrences);
        log.info("Asignación liberada: count={}", deallocated.size());
        return deallocated.stream()
                .map(d -> new DeallocatedOccurrenceDto(d.occurrenceId(), d.classroomId()))
                .toList();
    }

    @Override
    @Transactional
    public int syncFromSysacad(List<AllocationItem> items) {
        AllocationCommand command = new AllocationCommand(items, null, AllocationSource.SYSACAD);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolveAndValidate(command);
        int affected = writer.syncFromSysacad(classroomByOccurrence);
        log.info("Sync de SysAcad: asignaciones afectadas={}", affected);
        return affected;
    }

    private Map<OccurrenceSlotDto, Long> resolveAndValidate(AllocationCommand command) {
        // IMPORTED (ingest Excel) y SYSACAD no clampean: el primer sync/import de una comisión a mitad
        // de año trae ocurrencias ya pasadas, y rechazarlas dejaría esos slots sin asignación (plan §4).
        LocalDate clampFrom = (command.source() == AllocationSource.IMPORTED || command.source() == AllocationSource.SYSACAD)
                ? null : LocalDate.now();
        Map<OccurrenceSlotDto, Long> classroomByOccurrence =
                targetResolver.resolveClassroomByOccurrence(command.items(), clampFrom);

        if (classroomByOccurrence.isEmpty()) {
            return classroomByOccurrence;
        }

        Set<Long> classroomIds = Set.copyOf(classroomByOccurrence.values());
        requireAccessToClassrooms(classroomIds);
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

    private void requireAccessToClassrooms(Set<Long> classroomIds) {
        if (classroomIds.isEmpty()) {
            return;
        }
        Set<Long> buildingIds = classroomService.findByIds(classroomIds).stream()
                .map(ClassroomResponseDto::buildingId)
                .collect(Collectors.toSet());
        buildingScopeResolver.requireAccess(Permission.ALLOCATION_WRITE, buildingIds);
    }

    private void requireAccessToCurrentClassrooms(List<OccurrenceSlotDto> occurrences) {
        if (occurrences.isEmpty()) {
            return;
        }
        List<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        Set<Long> classroomIds = allocationRepository.findByOccurrenceIdIn(occurrenceIds).stream()
                .map(Allocation::getClassroomId)
                .collect(Collectors.toSet());
        requireAccessToClassrooms(classroomIds);
    }
}
