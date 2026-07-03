package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.dto.request.AllocationParametersDto;
import ar.edu.utn.frc.siga.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.siga.solver.dto.request.PinnedAssignmentDto;
import ar.edu.utn.frc.siga.solver.dto.response.AllocationPreviewResponseDto;
import ar.edu.utn.frc.siga.solver.exception.InvalidAllocationRequestException;
import ar.edu.utn.frc.siga.solver.exception.SchedulingException;
import ar.edu.utn.frc.siga.solver.mapper.AllocationRequestMapper;
import ar.edu.utn.frc.siga.solver.mapper.AllocationResponseMapper;
import ar.edu.utn.frc.siga.solver.model.ConflictPair;
import ar.edu.utn.frc.siga.solver.optimization.ClassAssignment;
import ar.edu.utn.frc.siga.solver.optimization.ClassroomConstraintProvider;
import ar.edu.utn.frc.siga.solver.optimization.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import ar.edu.utn.frc.siga.solver.optimization.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolverServiceImpl implements SolverService {

    private final AllocationRequestMapper requestMapper;
    private final AllocationResponseMapper responseMapper;

    @Override
    public AllocationPreviewResponseDto preview(AllocationRequestDto request) {
        AllocationParametersDto params = request.getParameters() != null
                ? request.getParameters() : new AllocationParametersDto();

        validateBusinessRules(request, params);

        List<SolverEvent> events = requestMapper.toEvents(request.getEvents());
        List<SolverRoom> classrooms = requestMapper.toClassrooms(request.getClassrooms(), params);
        Set<ConflictPair> conflicts = computeConflicts(events);
        Map<String, List<SolverRoom>> candidates = buildCandidates(events, classrooms, params);

        log.info("Starting solver preview: {} events, {} classrooms, limit {}s",
                events.size(), classrooms.size(), params.getTimeLimitSeconds());

        long start = System.currentTimeMillis();
        ScheduleSolution solution = solve(events, classrooms, conflicts, candidates, params.getTimeLimitSeconds());
        long durationMs = System.currentTimeMillis() - start;

        log.info("Solver preview completed in {}ms, score {}", durationMs, solution.getScore());

        return responseMapper.toPreviewResponse(solution, request, durationMs);
    }

    private ScheduleSolution solve(List<SolverEvent> events,
                                   List<SolverRoom> classrooms,
                                   Set<ConflictPair> conflicts,
                                   Map<String, List<SolverRoom>> candidatesByEventId,
                                   int timeLimitSeconds) {
        List<ClassAssignment> assignments = events.stream()
                .map(event -> buildAssignment(event, classrooms, conflicts, candidatesByEventId))
                .toList();
        ScheduleSolution problem = new ScheduleSolution(classrooms, assignments, null);
        return runSolver(problem, timeLimitSeconds);
    }

    private ClassAssignment buildAssignment(SolverEvent event,
                                            List<SolverRoom> classrooms,
                                            Set<ConflictPair> conflicts,
                                            Map<String, List<SolverRoom>> candidatesByEventId) {
        Set<String> conflictingIds = conflicts.stream()
                .filter(p -> p.involves(event.planningId()))
                .map(p -> p.otherEventId(event.planningId()))
                .collect(Collectors.toSet());
        List<SolverRoom> candidates = candidatesByEventId.getOrDefault(event.planningId(), classrooms);
        return new ClassAssignment(event, candidates, conflictingIds);
    }

    private ScheduleSolution runSolver(ScheduleSolution problem, int timeLimitSeconds) {
        String jobId = UUID.randomUUID().toString();
        log.info("Solver job {} starting, limit {}s", jobId, timeLimitSeconds);

        ai.timefold.solver.core.config.solver.SolverConfig config =
                new ai.timefold.solver.core.config.solver.SolverConfig()
                        .withSolutionClass(ScheduleSolution.class)
                        .withEntityClasses(ClassAssignment.class)
                        .withConstraintProviderClass(ClassroomConstraintProvider.class)
                        .withTerminationConfig(new TerminationConfig()
                                .withSecondsSpentLimit((long) timeLimitSeconds));

        SolverFactory<ScheduleSolution> factory = SolverFactory.create(config);
        Solver<ScheduleSolution> solver = factory.buildSolver();
        try {
            ScheduleSolution solution = solver.solve(problem);
            log.info("Solver job {} finished, score {}", jobId, solution.getScore());
            return solution;
        } catch (Exception e) {
            log.error("Solver job {} failed", jobId, e);
            throw new SchedulingException("Error during optimization: " + e.getMessage(), e);
        }
    }

    private Set<ConflictPair> computeConflicts(List<SolverEvent> events) {
        Set<ConflictPair> conflicts = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                SolverEvent a = events.get(i);
                SolverEvent b = events.get(j);
                if (!timesOverlap(a, b)) continue;
                if (b.occurrenceDates().stream().anyMatch(a.occurrenceDates()::contains)) {
                    conflicts.add(new ConflictPair(a.planningId(), b.planningId()));
                }
            }
        }
        return conflicts;
    }

    private Map<String, List<SolverRoom>> buildCandidates(List<SolverEvent> events,
                                                          List<SolverRoom> classrooms,
                                                          AllocationParametersDto params) {
        Map<Integer, SolverRoom> classroomById = classrooms.stream()
                .collect(Collectors.toMap(SolverRoom::id, c -> c));

        Map<String, Integer> pinnedMap = new HashMap<>();
        if (params.getPinnedAssignments() != null) {
            for (PinnedAssignmentDto pin : params.getPinnedAssignments()) {
                pinnedMap.put(pin.getEventId(), pin.getClassroomId());
            }
        }

        Map<String, List<SolverRoom>> candidates = new HashMap<>();
        for (SolverEvent event : events) {
            Integer pinnedId = pinnedMap.get(event.planningId());
            if (pinnedId != null && classroomById.containsKey(pinnedId)) {
                candidates.put(event.planningId(), List.of(classroomById.get(pinnedId)));
            } else {
                candidates.put(event.planningId(), classrooms);
            }
        }
        return candidates;
    }

    private boolean timesOverlap(SolverEvent a, SolverEvent b) {
        return a.startTime().isBefore(b.endTime())
                && b.startTime().isBefore(a.endTime());
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
