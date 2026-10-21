package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.ReleaseCommand;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.util.Plurals;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final ClassroomAllocationLock classroomLock;

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
    public Optional<Long> findClassroomIdByOccurrence(Long occurrenceId) {
        return allocationRepository.findByOccurrenceId(occurrenceId).map(Allocation::getClassroomId);
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
    @Transactional(readOnly = true)
    public List<AllocationResponseDto> findByOccurrenceIds(Collection<Long> occurrenceIds) {
        if (occurrenceIds.isEmpty()) {
            return List.of();
        }
        return composer.composeAll(allocationRepository.findByOccurrenceIdIn(occurrenceIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccurrenceSlotDto> resolveOccurrences(AllocationTarget target) {
        return targetResolver.resolveAll(List.of(target), LocalDate.now());
    }

    @Override
    @Transactional
    @AuditOperation("Asignación de aulas en lote")
    public List<AllocationResponseDto> allocate(AllocationCommand command) {
        AllocationTargetResolver.ResolvedAssignments resolved = resolve(command);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolved.classroomByOccurrence();
        List<ClassroomResponseDto> classrooms = lockAndValidate(command, classroomByOccurrence);
        describeAllocation("Asignación", classrooms, resolved.skippedReleased().size());
        List<Allocation> saved = writer.create(classroomByOccurrence, command.observation(), command.source());
        log.info("Asignación creada: source={}, count={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    @AuditOperation("Reasignación de aulas en lote")
    public List<AllocationResponseDto> reallocate(AllocationCommand command) {
        AllocationTargetResolver.ResolvedAssignments resolved = resolve(command);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolved.classroomByOccurrence();
        List<ClassroomResponseDto> classrooms = lockAndValidate(command, classroomByOccurrence);
        describeAllocation("Reasignación", classrooms, resolved.skippedReleased().size());
        List<Allocation> saved = writer.upsert(classroomByOccurrence, command.observation(), command.source());
        log.info("Asignación actualizada: source={}, count={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    @AuditOperation("Reasignación de aulas en lote")
    public List<AllocationResponseDto> reallocateExisting(AllocationCommand command) {
        AllocationTargetResolver.ResolvedAssignments resolved = resolve(command);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolved.classroomByOccurrence();
        Map<Long, Long> current = requirePreviousAllocation(classroomByOccurrence.keySet());
        requireAccessToClassrooms(Set.copyOf(current.values()));
        List<ClassroomResponseDto> classrooms = lockAndValidate(command, classroomByOccurrence);
        describeAllocation("Reasignación", classrooms, resolved.skippedReleased().size());
        List<Allocation> saved = writer.upsert(classroomByOccurrence, command.observation(), command.source());
        log.info("Asignación actualizada: source={}, count={}", command.source(), saved.size());
        return composer.composeAll(saved);
    }

    @Override
    @Transactional
    @AuditOperation("Liberación de aulas en lote")
    public List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command) {
        List<OccurrenceSlotDto> occurrences = targetResolver.resolveAll(command.targets(), null);
        AuditOperations.describe("Liberación de " + Plurals.count(occurrences.size(), "ocurrencia", "ocurrencias"));
        occurrences.forEach(validator::validateNotPast);
        requireAccessToCurrentClassrooms(occurrences);

        List<AllocationWriter.DeallocatedOccurrence> deallocated = writer.delete(occurrences, command.observation());
        log.info("Asignación liberada: count={}", deallocated.size());
        return deallocated.stream()
                .map(d -> new DeallocatedOccurrenceDto(d.occurrenceId(), d.classroomId()))
                .toList();
    }

    @Override
    @Transactional
    @AuditOperation("Liberación de ocurrencias con motivo")
    public List<ReleasedOccurrenceDto> release(ReleaseCommand command) {
        Map<Long, OccurrenceSlotDto> byId = new LinkedHashMap<>();
        List<OccurrenceSlotDto> principals = targetResolver.resolveAll(command.targets(), LocalDate.now());
        principals.forEach(o -> byId.put(o.occurrenceId(), o));
        occurrenceService.findMirrors(byId.keySet()).forEach(o -> byId.putIfAbsent(o.occurrenceId(), o));
        List<OccurrenceSlotDto> occurrences = List.copyOf(byId.values());
        AuditOperations.describe("Liberación de " + Plurals.count(occurrences.size(), "ocurrencia", "ocurrencias")
                + " (" + command.reason() + ")");
        occurrences.forEach(validator::validateNotPast);

        Map<Long, Long> classroomByOccurrence = currentClassroomByOccurrence(occurrences);
        requireAccessToOwningBuildings(occurrences, classroomByOccurrence);

        writer.delete(occurrences, command.note());
        occurrenceService.release(
                occurrences.stream()
                        .map(o -> new OccurrenceRelease(o.occurrenceId(),
                                classroomByOccurrence.getOrDefault(o.occurrenceId(), o.previousClassroomId())))
                        .toList(),
                command.reason(), command.note());
        log.info("Ocurrencias liberadas: reason={}, count={}", command.reason(), occurrences.size());
        return occurrences.stream()
                .map(o -> new ReleasedOccurrenceDto(o.occurrenceId(), o.eventId(), o.date(),
                        classroomByOccurrence.get(o.occurrenceId()), command.reason()))
                .toList();
    }

    @Override
    @Transactional
    @AuditOperation("Reversión de liberación de ocurrencias")
    public List<Long> cancelRelease(DeallocationCommand command) {
        // Los espejos nunca vuelven a pedir aula solos: los recrea la resolución de un pedido.
        List<OccurrenceSlotDto> released = targetResolver.resolveAll(command.targets(), LocalDate.now()).stream()
                .filter(o -> o.status() == OccurrenceStatus.ROOM_RELEASED)
                .filter(o -> o.mirrorOfOccurrenceId() == null)
                .toList();
        AuditOperations.describe("Reversión de liberación de "
                + Plurals.count(released.size(), "ocurrencia", "ocurrencias"));
        requireAccessToOwningBuildings(released, currentClassroomByOccurrence(released));

        List<Long> ids = released.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        occurrenceService.requestRoom(ids);
        log.info("Liberación revertida: count={}", ids.size());
        return ids;
    }

    @Override
    @Transactional
    @AuditOperation("Sincronización de asignaciones desde SysAcad")
    public int syncFromSysacad(List<AllocationItem> items) {
        AllocationCommand command = new AllocationCommand(items, null, AllocationSource.SYSACAD);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = resolve(command).classroomByOccurrence();
        lockAndValidate(command, classroomByOccurrence);
        int affected = writer.syncFromSysacad(classroomByOccurrence);
        AuditOperations.describe("Sincronización de asignaciones desde SysAcad: " + Plurals.count(affected, "afectada", "afectadas"));
        log.info("Sync de SysAcad: asignaciones afectadas={}", affected);
        return affected;
    }

    /** E.g. "Reasignación de 12 aulas, edificio Central" or "..., 3 edificios". */
    private void describeAllocation(String action, List<ClassroomResponseDto> classrooms, int skippedReleased) {
        if (classrooms.isEmpty()) {
            return;
        }
        List<String> buildings = classrooms.stream().map(ClassroomResponseDto::buildingName).distinct().toList();
        AuditOperations.describe(action + " de " + Plurals.count(classrooms.size(), "aula", "aulas")
                + (buildings.size() == 1 ? ", edificio " + buildings.get(0) : ", " + Plurals.count(buildings.size(), "edificio", "edificios"))
                + (skippedReleased > 0 ? ", " + Plurals.count(skippedReleased, "liberada salteada", "liberadas salteadas") : ""));
    }

    private AllocationTargetResolver.ResolvedAssignments resolve(AllocationCommand command) {
        LocalDate clampFrom = command.source().allowsPastOccurrences() ? null : LocalDate.now();
        AllocationTargetResolver.ResolvedAssignments resolved =
                targetResolver.resolveClassroomByOccurrence(command.items(), clampFrom);
        List<OccurrenceSlotDto> skipped = resolved.skippedReleased();
        if (!skipped.isEmpty()) {
            log.warn("Ocurrencias liberadas salteadas: source={}, count={}, ids={}", command.source(),
                    skipped.size(), skipped.stream().map(OccurrenceSlotDto::occurrenceId).toList());
        }
        return resolved;
    }

    /** Locks and validates the target classrooms; returns them (empty if there is nothing to allocate). */
    private List<ClassroomResponseDto> lockAndValidate(AllocationCommand command,
            Map<OccurrenceSlotDto, Long> classroomByOccurrence) {
        if (classroomByOccurrence.isEmpty()) {
            return List.of();
        }

        Set<Long> classroomIds = Set.copyOf(classroomByOccurrence.values());
        classroomLock.lock(classroomIds);
        List<ClassroomResponseDto> classrooms = requireAccessToClassrooms(classroomIds);
        validator.validateClassroomsAvailable(classroomIds);

        if (command.source() == AllocationSource.MANUAL || command.source() == AllocationSource.AUTOMATIC) {
            List<AllocationCandidate> candidates = classroomByOccurrence.entrySet().stream()
                    .map(e -> new AllocationCandidate(e.getKey(), e.getValue()))
                    .toList();
            if (command.source() == AllocationSource.MANUAL) {
                validator.validateManualOverlap(candidates, command.observation());
            } else {
                validator.validateStrictOverlap(candidates);
            }
        }
        return classrooms;
    }

    private Allocation findAllocation(Long id) {
        return Finder.orThrow(allocationRepository::findById, id, "Allocation");
    }

    private List<ClassroomResponseDto> requireAccessToClassrooms(Set<Long> classroomIds) {
        if (classroomIds.isEmpty()) {
            return List.of();
        }
        List<ClassroomResponseDto> classrooms = classroomService.findByIdsIncludingDeactivated(classroomIds);
        Set<Long> buildingIds = classrooms.stream()
                .map(ClassroomResponseDto::buildingId)
                .collect(Collectors.toSet());
        buildingScopeResolver.requireAccess(Permission.ALLOCATION_WRITE, buildingIds);
        return classrooms;
    }

    private Map<Long, Long> requirePreviousAllocation(Collection<OccurrenceSlotDto> occurrences) {
        Map<Long, Long> current = currentClassroomByOccurrence(List.copyOf(occurrences));
        List<Long> missing = occurrences.stream()
                .map(OccurrenceSlotDto::occurrenceId)
                .filter(id -> !current.containsKey(id))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new AllocationConflictException("Las ocurrencias " + missing
                    + " no tienen aula asignada: para asignarlas usá POST /v1/allocations.");
        }
        return current;
    }

    private Map<Long, Long> currentClassroomByOccurrence(List<OccurrenceSlotDto> occurrences) {
        if (occurrences.isEmpty()) {
            return Map.of();
        }
        List<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        return allocationRepository.findByOccurrenceIdIn(occurrenceIds).stream()
                .collect(Collectors.toMap(Allocation::getOccurrenceId, Allocation::getClassroomId));
    }

    /**
     * Aula asignada o, si no tiene, el aula anterior guardada al liberar. Sin ninguna de las dos no
     * hay edificio al que validar y la operación pasa.
     */
    private void requireAccessToOwningBuildings(List<OccurrenceSlotDto> occurrences,
            Map<Long, Long> classroomByOccurrence) {
        Set<Long> classroomIds = occurrences.stream()
                .map(o -> classroomByOccurrence.getOrDefault(o.occurrenceId(), o.previousClassroomId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        requireAccessToClassrooms(classroomIds);
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
