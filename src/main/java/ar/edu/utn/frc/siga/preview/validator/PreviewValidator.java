package ar.edu.utn.frc.siga.preview.validator;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto.ConflictOrigin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class PreviewValidator {

    public record ResolvedProposal(Long eventId, Long classroomId, List<LocalDate> dates,
                                    LocalTime startTime, LocalTime endTime) implements TimeSpan {
    }

    private record Moved(Long classroomId, LocalTime startTime, LocalTime endTime) implements TimeSpan {
    }


    public void validateNoDuplicateEventIds(List<PreviewAllocationDto> allocations) {
        List<Long> eventIds = allocations.stream().map(PreviewAllocationDto::eventId).toList();
        if (new HashSet<>(eventIds).size() != eventIds.size()) {
            throw new AllocationConflictException("La propuesta final tiene eventos duplicados.");
        }
    }

    public void validateAllocationsBelongToPreview(List<PreviewAllocationDto> allocations, Set<Long> previewEventIds) {
        Set<Long> foreign = foreignIds(allocations.stream().map(PreviewAllocationDto::eventId), previewEventIds);
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign + " no pertenecen al preview indicado");
        }
    }

    private Set<Long> foreignIds(Stream<Long> ids, Set<Long> previewEventIds) {
        return ids.filter(id -> !previewEventIds.contains(id)).collect(Collectors.toSet());
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
