package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.RoomDate;
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

    public void validateNoOverlap(List<AllocationCandidate> candidates) {
        List<AllocationCandidate> future = candidates.stream().filter(c -> !c.occurrence().isPast()).toList();
        if (future.isEmpty()) return;

        LocalDate min = future.stream().map(c -> c.occurrence().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = future.stream().map(c -> c.occurrence().date()).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> ownOccurrenceIds = future.stream().map(c -> c.occurrence().occurrenceId()).collect(Collectors.toSet());

        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsBetween(min, max),
                OccurrenceSlotDto::occurrenceId);

        List<OccupiedSlot> occupancy = allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet())
                .stream()
                .filter(a -> !ownOccurrenceIds.contains(a.getOccurrenceId()))
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();

        validateNoOverlap(future, occupancy);
    }

    public void validateNoOverlap(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(databaseConflicts(candidates, occupancy));
        conflicts.addAll(internalConflicts(candidates));

        if (!conflicts.isEmpty()) {
            throw new ReallocationConflictException(conflicts);
        }
    }

    List<OccurrenceConflictDto> databaseConflicts(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        return Clashes.between(candidates, AllocationValidator::candidateKey,
                occupancy, occupied -> List.of(new RoomDate(occupied.classroomId(), occupied.date())),
                (c, o) -> true,
                (c, o, key) -> new OccurrenceConflictDto(c.occurrence().occurrenceId(), key.date(),
                        c.startTime(), c.endTime(), c.classroomId(), o.eventId(), o.allocationId()));
    }

    List<OccurrenceConflictDto> internalConflicts(List<AllocationCandidate> candidates) {
        return Clashes.within(candidates, AllocationValidator::candidateKey,
                (a, b) -> !a.occurrence().eventId().equals(b.occurrence().eventId()),
                (a, b, key) -> new OccurrenceConflictDto(a.occurrence().occurrenceId(), key.date(),
                        a.startTime(), a.endTime(), a.classroomId(), b.occurrence().eventId(), null));
    }

    private static List<RoomDate> candidateKey(AllocationCandidate candidate) {
        return List.of(new RoomDate(candidate.classroomId(), candidate.occurrence().date()));
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
