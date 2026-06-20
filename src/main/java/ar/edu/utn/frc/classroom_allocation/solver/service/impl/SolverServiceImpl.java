package ar.edu.utn.frc.classroom_allocation.solver.service.impl;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.AllocationParametersDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.request.PinnedAssignmentDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;
import ar.edu.utn.frc.classroom_allocation.solver.exception.InvalidAllocationRequestException;
import ar.edu.utn.frc.classroom_allocation.solver.mapper.AllocationRequestMapper;
import ar.edu.utn.frc.classroom_allocation.solver.mapper.AllocationResponseMapper;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.solver.model.ConflictPair;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.solver.optimization.ClassroomAllocationSolver;
import ar.edu.utn.frc.classroom_allocation.solver.optimization.impl.ScheduleSolution;
import ar.edu.utn.frc.classroom_allocation.solver.service.SolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolverServiceImpl implements SolverService {

    private final ClassroomAllocationSolver solver;
    private final AllocationRequestMapper requestMapper;
    private final AllocationResponseMapper responseMapper;

    @Override
    public AllocationPreviewResponseDto preview(AllocationRequestDto request) {
        AllocationParametersDto params = request.getParameters() != null
                ? request.getParameters() : new AllocationParametersDto();

        validateBusinessRules(request, params);

        List<AcademicEvent> events = requestMapper.toEvents(request.getEvents());
        List<Classroom> classrooms = requestMapper.toClassrooms(request.getClassrooms(), params);
        Set<ConflictPair> conflicts = computeConflicts(events);
        Map<String, List<Classroom>> candidates = buildCandidates(events, classrooms, params);

        log.info("Starting solver preview: {} events, {} classrooms, limit {}s",
                events.size(), classrooms.size(), params.getTimeLimitSeconds());

        long start = System.currentTimeMillis();
        ScheduleSolution solution = solver.solve(events, classrooms, conflicts, candidates, params.getTimeLimitSeconds());
        long durationMs = System.currentTimeMillis() - start;

        log.info("Solver preview completed in {}ms, score {}", durationMs, solution.getScore());

        return responseMapper.toPreviewResponse(solution, request, durationMs);
    }

    private Set<ConflictPair> computeConflicts(List<AcademicEvent> events) {
        Set<ConflictPair> conflicts = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                AcademicEvent a = events.get(i);
                AcademicEvent b = events.get(j);
                if (!timesOverlap(a, b)) continue;
                Set<LocalDate> datesA = a.toOccurrences().stream()
                        .map(Occurrence::getDate)
                        .collect(Collectors.toSet());
                if (b.toOccurrences().stream().map(Occurrence::getDate).anyMatch(datesA::contains)) {
                    conflicts.add(new ConflictPair(a.getPlanningId(), b.getPlanningId()));
                }
            }
        }
        return conflicts;
    }

    private Map<String, List<Classroom>> buildCandidates(List<AcademicEvent> events,
                                                          List<Classroom> classrooms,
                                                          AllocationParametersDto params) {
        Map<Integer, Classroom> classroomById = classrooms.stream()
                .collect(Collectors.toMap(Classroom::getId, c -> c));

        Map<String, Integer> pinnedMap = new HashMap<>();
        if (params.getPinnedAssignments() != null) {
            for (PinnedAssignmentDto pin : params.getPinnedAssignments()) {
                pinnedMap.put(pin.getEventId(), pin.getClassroomId());
            }
        }

        Map<String, List<Classroom>> candidates = new HashMap<>();
        for (AcademicEvent event : events) {
            Integer pinnedId = pinnedMap.get(event.getPlanningId());
            if (pinnedId != null && classroomById.containsKey(pinnedId)) {
                candidates.put(event.getPlanningId(), List.of(classroomById.get(pinnedId)));
            } else {
                candidates.put(event.getPlanningId(), classrooms);
            }
        }
        return candidates;
    }

    private boolean timesOverlap(AcademicEvent a, AcademicEvent b) {
        return a.getStartTime().isBefore(b.endTime())
                && b.getStartTime().isBefore(a.endTime());
    }

    private void validateBusinessRules(AllocationRequestDto request, AllocationParametersDto params) {
        Set<String> eventIds = new HashSet<>();
        for (var e : request.getEvents()) {
            if (!eventIds.add(e.getId())) {
                throw new InvalidAllocationRequestException("Duplicate event id: " + e.getId());
            }
        }

        Set<Integer> classroomIds = new HashSet<>();
        for (var c : request.getClassrooms()) {
            if (!classroomIds.add(c.getId())) {
                throw new InvalidAllocationRequestException("Duplicate classroom id: " + c.getId());
            }
        }

        for (PinnedAssignmentDto pin : params.getPinnedAssignments()) {
            if (!eventIds.contains(pin.getEventId())) {
                throw new InvalidAllocationRequestException(
                        "Pinned eventId not found in events list: " + pin.getEventId());
            }
            if (!classroomIds.contains(pin.getClassroomId())) {
                throw new InvalidAllocationRequestException(
                        "Pinned classroomId not found in classrooms list: " + pin.getClassroomId());
            }
            if (params.getExcludedClassroomIds().contains(pin.getClassroomId())) {
                throw new InvalidAllocationRequestException(
                        "Classroom " + pin.getClassroomId() + " is both pinned and excluded");
            }
        }

        long pinnedCount = params.getPinnedAssignments().stream()
                .map(PinnedAssignmentDto::getEventId)
                .distinct().count();
        if (pinnedCount < params.getPinnedAssignments().size()) {
            throw new InvalidAllocationRequestException("An event appears more than once in pinnedAssignments");
        }
    }
}
