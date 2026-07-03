package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
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
import ar.edu.utn.frc.siga.solver.optimization.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import ar.edu.utn.frc.siga.solver.optimization.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolverServiceImpl implements SolverService {

    private final AllocationRequestMapper requestMapper;
    private final AllocationResponseMapper responseMapper;
    private final SolverManager<ScheduleSolution> solverManager;
    private final SolverProperties solverProperties;

    @Override
    public AllocationPreviewResponseDto preview(AllocationRequestDto request) {
        AllocationParametersDto params = request.getParameters() != null
                ? request.getParameters() : new AllocationParametersDto();

        validateBusinessRules(request, params);

        List<SolverEvent> events = requestMapper.toEvents(request.getEvents());
        List<SolverRoom> classrooms = requestMapper.toClassrooms(request.getClassrooms(), params);
        Map<String, Set<String>> conflictsByEventId = toAdjacency(computeConflicts(events));
        Map<String, List<SolverRoom>> candidates = buildCandidates(events, classrooms, params);

        log.info("Starting solver preview: {} events, {} classrooms, limit {}s",
                events.size(), classrooms.size(), params.getTimeLimitSeconds());

        long start = System.currentTimeMillis();
        ScheduleSolution solution = solve(events, classrooms, conflictsByEventId, candidates, params.getTimeLimitSeconds());
        long durationMs = System.currentTimeMillis() - start;

        log.info("Solver preview completed in {}ms, score {}", durationMs, solution.getScore());

        return responseMapper.toPreviewResponse(solution, request, durationMs);
    }

    private ScheduleSolution solve(List<SolverEvent> events,
                                   List<SolverRoom> classrooms,
                                   Map<String, Set<String>> conflictsByEventId,
                                   Map<String, List<SolverRoom>> candidatesByEventId,
                                   int timeLimitSeconds) {
        List<ClassAssignment> assignments = events.stream()
                .map(event -> new ClassAssignment(
                        event,
                        candidatesByEventId.getOrDefault(event.planningId(), classrooms),
                        conflictsByEventId.getOrDefault(event.planningId(), Set.of())))
                .toList();
        ScheduleSolution problem = new ScheduleSolution(classrooms, assignments);
        return runSolver(problem, timeLimitSeconds);
    }

    private ScheduleSolution runSolver(ScheduleSolution problem, int timeLimitSeconds) {
        String jobId = UUID.randomUUID().toString();
        log.info("Solver job {} starting, limit {}s", jobId, timeLimitSeconds);

        SolverJob<ScheduleSolution> job = solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride()
                        .withTerminationConfig(buildTermination(timeLimitSeconds)))
                .run();
        try {
            ScheduleSolution solution = job.getFinalBestSolution();
            log.info("Solver job {} finished, score {}", jobId, solution.getScore());
            return solution;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.terminateEarly();
            throw new SchedulingException("Optimization interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Solver job {} failed", jobId, cause);
            throw new SchedulingException("Error during optimization: " + cause.getMessage(), cause);
        }
    }

    private TerminationConfig buildTermination(int timeLimitSeconds) {
        TerminationConfig termination = new TerminationConfig()
                .withSecondsSpentLimit((long) timeLimitSeconds);
        long unimproved = solverProperties.getUnimprovedSecondsLimit();
        if (unimproved > 0) {
            termination.setUnimprovedSecondsSpentLimit(unimproved);
        }
        return termination;
    }

    /**
     * Agrupa los eventos por fecha de ocurrencia y detecta solapamientos con un barrido
     * ordenado por hora de inicio dentro de cada fecha. Evita el producto cartesiano
     * de eventos x eventos con comparación de listas de fechas por par.
     */
    private Set<ConflictPair> computeConflicts(List<SolverEvent> events) {
        Map<LocalDate, List<SolverEvent>> eventsByDate = new HashMap<>();
        for (SolverEvent event : events) {
            for (LocalDate date : event.occurrenceDates()) {
                eventsByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(event);
            }
        }

        Set<ConflictPair> conflicts = new HashSet<>();
        for (List<SolverEvent> sameDate : eventsByDate.values()) {
            if (sameDate.size() < 2) continue;
            sameDate.sort(Comparator.comparing(SolverEvent::startTime));
            for (int i = 0; i < sameDate.size(); i++) {
                SolverEvent a = sameDate.get(i);
                for (int j = i + 1; j < sameDate.size(); j++) {
                    SolverEvent b = sameDate.get(j);
                    // Orden ascendente por inicio: si b arranca cuando a ya terminó,
                    // ningún evento posterior puede solapar con a.
                    if (!b.startTime().isBefore(a.endTime())) break;
                    if (a.planningId().equals(b.planningId())) continue;
                    if (timesOverlap(a, b)) {
                        conflicts.add(new ConflictPair(a.planningId(), b.planningId()));
                    }
                }
            }
        }
        return conflicts;
    }

    private Map<String, Set<String>> toAdjacency(Set<ConflictPair> conflicts) {
        Map<String, Set<String>> byEventId = new HashMap<>();
        for (ConflictPair pair : conflicts) {
            byEventId.computeIfAbsent(pair.eventIdA(), id -> new HashSet<>()).add(pair.eventIdB());
            byEventId.computeIfAbsent(pair.eventIdB(), id -> new HashSet<>()).add(pair.eventIdA());
        }
        return byEventId;
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
