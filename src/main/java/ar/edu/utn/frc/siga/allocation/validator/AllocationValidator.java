package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.config.AllocationSettings;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.exception.OverlapObservationRequiredException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@NamedInterface("api")
@Component
@RequiredArgsConstructor
public class AllocationValidator {

    private final ClassroomService classroomService;
    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;
    private final AllocationSettings allocationSettings;

    public void validateNoOverlap(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        throwIfAny(review(candidates, occupancy, 0).blocking());
    }

    public void validateManualOverlap(List<AllocationCandidate> candidates, String observation) {
        OverlapReview overlaps = reviewManualOverlaps(candidates);
        throwIfAny(overlaps.blocking());
        if (!overlaps.tolerated().isEmpty() && (observation == null || observation.isBlank())) {
            throw new OverlapObservationRequiredException(overlaps.tolerated());
        }
    }

    public OverlapReview reviewManualOverlaps(List<AllocationCandidate> candidates) {
        List<AllocationCandidate> future = candidates.stream().filter(c -> !c.occurrence().isPast()).toList();
        if (future.isEmpty()) return new OverlapReview(List.of(), List.of());
        return review(future, loadOccupancy(future), allocationSettings.getMaxOverlapMinutes());
    }

    private OverlapReview review(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy,
                                 int toleranceMinutes) {
        List<OccurrenceConflictDto> all = new ArrayList<>();
        all.addAll(databaseConflicts(candidates, occupancy));
        all.addAll(internalConflicts(candidates));
        Map<Boolean, List<OccurrenceConflictDto>> byExcess = all.stream()
                .collect(Collectors.partitioningBy(c -> c.overlapMinutes() > toleranceMinutes));
        return new OverlapReview(byExcess.get(true), byExcess.get(false));
    }

    private List<OccupiedSlot> loadOccupancy(List<AllocationCandidate> future) {
        LocalDate min = future.stream().map(c -> c.occurrence().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = future.stream().map(c -> c.occurrence().date()).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> ownOccurrenceIds = future.stream().map(c -> c.occurrence().occurrenceId()).collect(Collectors.toSet());

        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsBetween(min, max),
                OccurrenceSlotDto::occurrenceId);

        return allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet())
                .stream()
                .filter(a -> !ownOccurrenceIds.contains(a.getOccurrenceId()))
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();
    }

    private static void throwIfAny(List<OccurrenceConflictDto> conflicts) {
        if (!conflicts.isEmpty()) {
            throw new ReallocationConflictException(conflicts);
        }
    }

    List<OccurrenceConflictDto> databaseConflicts(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        return Clashes.between(candidates, AllocationValidator::candidateKey,
                occupancy, occupied -> List.of(new RoomDate(occupied.classroomId(), occupied.date())),
                (c, o) -> true,
                (c, o, key) -> new OccurrenceConflictDto(c.occurrence().occurrenceId(), key.date(),
                        c.startTime(), c.endTime(), c.classroomId(), o.eventId(), o.allocationId(),
                        o.occurrenceId(),
                        (int) TimeRanges.overlapMinutes(c.startTime(), c.endTime(), o.startTime(), o.endTime())));
    }

    List<OccurrenceConflictDto> internalConflicts(List<AllocationCandidate> candidates) {
        return Clashes.within(candidates, AllocationValidator::candidateKey,
                (a, b) -> !a.occurrence().eventId().equals(b.occurrence().eventId()),
                (a, b, key) -> new OccurrenceConflictDto(a.occurrence().occurrenceId(), key.date(),
                        a.startTime(), a.endTime(), a.classroomId(), b.occurrence().eventId(), null,
                        b.occurrence().occurrenceId(),
                        (int) TimeRanges.overlapMinutes(a.startTime(), a.endTime(), b.startTime(), b.endTime())));
    }

    private static List<RoomDate> candidateKey(AllocationCandidate candidate) {
        return List.of(new RoomDate(candidate.classroomId(), candidate.occurrence().date()));
    }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new InvalidDateRangeException("La reasignación por rango necesita una fecha de inicio.");
        }
        if (from.isBefore(LocalDate.now())) {
            throw new InvalidDateRangeException(
                    "No se puede reasignar desde el " + from + ": esa fecha ya pasó.");
        }
        DateRanges.requireNotBefore(to, from);
    }

    public void validateOccurrencesExist(List<Long> requestedIds, List<OccurrenceSlotDto> resolved) {
        if (requestedIds.size() == resolved.size()) return;

        Set<Long> resolvedIds = resolved.stream().map(OccurrenceSlotDto::occurrenceId).collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream().filter(id -> !resolvedIds.contains(id)).toList();
        throw new AllocationConflictException("La(s) ocurrencia(s) " + missing + " no existe(n).");
    }

    public void validateNotPast(OccurrenceSlotDto occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationConflictException(
                    "No se puede modificar la asignación: la ocurrencia del " + occurrence.date() + " ya ocurrió.");
        }
    }

    public void validateClassroomsAvailable(Set<Long> classroomIds) {
        Map<Long, ClassroomResponseDto> classroomsById =
                Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);
        for (Long classroomId : classroomIds) {
            ClassroomResponseDto classroom = classroomsById.get(classroomId);
            if (classroom == null) {
                throw new AllocationConflictException("El aula " + classroomId + " no existe o no está disponible.");
            }
        }
    }

}
