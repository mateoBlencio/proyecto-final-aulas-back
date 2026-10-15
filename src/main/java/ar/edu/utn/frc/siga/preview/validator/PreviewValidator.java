package ar.edu.utn.frc.siga.preview.validator;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.preview.service.impl.PlanningKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PreviewValidator {

    public record ResolvedProposal(Long eventId, Long classroomId, List<LocalDate> dates,
                                    LocalTime startTime, LocalTime endTime) implements TimeSpan {
    }

    private record Moved(Long classroomId, LocalTime startTime, LocalTime endTime) implements TimeSpan {
    }


    public void validateNoDuplicateAllocations(List<PreviewAllocationDto> allocations) {
        List<PlanningKey> keys = allocations.stream().map(PreviewValidator::keyOf).toList();
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new AllocationConflictException("La propuesta final tiene eventos duplicados.");
        }
    }

    public void validateAllocationsBelongToPreview(List<PreviewAllocationDto> allocations,
            Set<PlanningKey> previewKeys) {
        Set<PlanningKey> foreign = allocations.stream()
                .map(PreviewValidator::keyOf)
                .filter(key -> !previewKeys.contains(key))
                .collect(Collectors.toSet());
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign.stream().map(PlanningKey::toPlanningId).sorted().toList()
                            + " no pertenecen al preview indicado");
        }
    }

    // internalConflicts ignora los pares del mismo evento, así que dos espejos en la misma aula y fecha
    // pasarían como válidos.
    public void validateNoSiblingRoomClash(List<AllocationCandidate> candidates) {
        Map<List<Object>, Set<Integer>> roomSlotsByRoomDate = new HashMap<>();
        for (AllocationCandidate candidate : candidates) {
            OccurrenceSlotDto occurrence = candidate.occurrence();
            roomSlotsByRoomDate.computeIfAbsent(
                            List.of(occurrence.eventId(), candidate.classroomId(), occurrence.date()),
                            k -> new HashSet<>())
                    .add(occurrence.roomSlot());
        }
        boolean clash = roomSlotsByRoomDate.values().stream().anyMatch(slots -> slots.size() > 1);
        if (clash) {
            throw new AllocationConflictException(
                    "dos aulas simultáneas del mismo evento no pueden asignarse a la misma aula en la misma fecha");
        }
    }

    private static PlanningKey keyOf(PreviewAllocationDto allocation) {
        return new PlanningKey(allocation.eventId(), allocation.effectiveRoomSlot());
    }

    List<MoveConflictDto> moveDatabaseConflicts(Long destination, Set<LocalDate> movedDates,
            LocalTime movedStart, LocalTime movedEnd, List<OccupiedSlot> databaseOccupancy) {
        return conflictsAgainst(destination, movedDates, movedStart, movedEnd, databaseOccupancy,
                occupied -> List.of(new RoomDate(occupied.classroomId(), occupied.date())),
                OccupiedSlot::eventId, ConflictOrigin.DATABASE);
    }


    public List<MoveConflictDto> unresolvedConflicts(Set<Long> candidateRoomIds, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<OccupiedSlot> databaseOccupancy,
            List<ResolvedProposal> resolvedProposals) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (Long roomId : candidateRoomIds) {
            MoveConflictDto conflict = moveDatabaseConflicts(roomId, dates, start, end, databaseOccupancy).stream()
                    .min(Comparator.comparing(MoveConflictDto::date))
                    .orElse(null);
            if (conflict == null) {
                conflict = previewConflicts(roomId, dates, start, end, resolvedProposals).stream()
                        .min(Comparator.comparing(MoveConflictDto::date))
                        .orElse(null);
            }
            if (conflict != null) {
                conflicts.add(conflict);
            } else {
                log.warn("Aula candidata {} sin conflicto hallado para evento inubicable del preview ({}-{})",
                        roomId, start, end);
            }
        }
        return conflicts;
    }

    public record RoomOverlap(Long classroomId, int overlapMinutes, Long conflictingEventId) {
    }

    // Por aula, el peor solape de la fecha contra la base y contra las otras propuestas del preview.
    // Descarta las que superan maxOverlapMinutes; las libres vuelven con 0 y sin evento.
    public List<RoomOverlap> roomOverlaps(Set<Long> candidateRoomIds, LocalDate date, LocalTime start, LocalTime end,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals,
            int maxOverlapMinutes) {
        List<RoomOverlap> overlaps = new ArrayList<>();
        for (Long roomId : candidateRoomIds) {
            RoomOverlap worst = new RoomOverlap(roomId, 0, null);
            for (OccupiedSlot occupied : databaseOccupancy) {
                if (roomId.equals(occupied.classroomId()) && date.equals(occupied.date())) {
                    worst = worse(worst, roomId, start, end, occupied, occupied.eventId());
                }
            }
            for (ResolvedProposal proposal : resolvedProposals) {
                if (roomId.equals(proposal.classroomId()) && proposal.dates().contains(date)) {
                    worst = worse(worst, roomId, start, end, proposal, proposal.eventId());
                }
            }
            if (worst.overlapMinutes() <= maxOverlapMinutes) {
                overlaps.add(worst);
            }
        }
        overlaps.sort(Comparator.comparingInt(RoomOverlap::overlapMinutes).thenComparing(RoomOverlap::classroomId));
        return overlaps;
    }

    private static RoomOverlap worse(RoomOverlap current, Long roomId, LocalTime start, LocalTime end,
            TimeSpan occupant, Long occupantEventId) {
        int minutes = (int) TimeRanges.overlapMinutes(start, end, occupant.startTime(), occupant.endTime());
        return minutes > current.overlapMinutes() ? new RoomOverlap(roomId, minutes, occupantEventId) : current;
    }

    private List<MoveConflictDto> previewConflicts(Long classroomId, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<ResolvedProposal> resolvedProposals) {
        return conflictsAgainst(classroomId, dates, start, end, resolvedProposals,
                PreviewValidator::proposalKeys, ResolvedProposal::eventId, ConflictOrigin.PREVIEW);
    }

    private <B extends TimeSpan> List<MoveConflictDto> conflictsAgainst(Long classroomId, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<B> occupants,
            Function<B, List<RoomDate>> keysOf, Function<B, Long> eventIdOf,
            ConflictOrigin origin) {
        Moved moved = new Moved(classroomId, start, end);
        return Clashes.between(List.of(moved), m -> movedKeys(classroomId, dates),
                occupants, keysOf,
                (m, o) -> true,
                (m, o, key) -> new MoveConflictDto(key.date(), o.startTime(), o.endTime(),
                        key.classroomId(), eventIdOf.apply(o), origin));
    }

    private static List<RoomDate> movedKeys(Long classroomId, Set<LocalDate> dates) {
        return dates.stream().map(date -> new RoomDate(classroomId, date)).toList();
    }

    private static List<RoomDate> proposalKeys(ResolvedProposal proposal) {
        if (proposal.classroomId() == null) return List.of();
        return proposal.dates().stream().map(date -> new RoomDate(proposal.classroomId(), date)).toList();
    }

}
