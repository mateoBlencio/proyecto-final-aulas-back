package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.config.OptimizerSettings;
import ar.edu.utn.frc.siga.optimizer.exception.SchedulingException;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizerServiceImpl implements OptimizerService {

    private final SolverManager<ScheduleSolution> solverManager;
    private final OptimizerSettings optimizerSettings;

    private record ExistingOccupancy(OptimizerEvent event, OptimizerRoom room) {
    }

    @Override
    public OptimizationResult optimize(List<OptimizerEvent> events, List<OptimizerRoom> classrooms,
                                 List<OptimizerOccupancy> occupancy, int timeLimitSeconds) {
        Map<Long, OptimizerRoom> roomsById = classrooms.stream()
                .collect(Collectors.toMap(OptimizerRoom::id, r -> r));
        List<ExistingOccupancy> existing = buildExistingOccupancy(occupancy, roomsById);

        List<OptimizerEvent> allEvents = new ArrayList<>(events);
        existing.forEach(e -> allEvents.add(e.event()));
        Map<String, Set<String>> conflictsByEventId = computeConflicts(allEvents);

        log.info("Iniciando optimización: {} eventos, {} aulas, {} franjas ocupadas, límite {}s",
                events.size(), classrooms.size(), existing.size(), timeLimitSeconds);

        long start = System.currentTimeMillis();
        ScheduleSolution solution = solve(events, existing, classrooms, conflictsByEventId, timeLimitSeconds);
        log.info("Optimización completada en {}ms, score {}",
                System.currentTimeMillis() - start, solution.getScore());

        return toResult(solution);
    }

    private OptimizationResult toResult(ScheduleSolution solution) {
        List<OptimizerAllocation> allocations = solution.getAllocations().stream()
                .filter(a -> !a.isPinned())
                .map(a -> new OptimizerAllocation(
                        a.getEvent().planningId(),
                        a.getClassroom() != null ? a.getClassroom().id() : null))
                .toList();
        String previewId = "prev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new OptimizationResult(previewId, allocations);
    }

    private List<ExistingOccupancy> buildExistingOccupancy(List<OptimizerOccupancy> occupancy,
                                                           Map<Long, OptimizerRoom> roomsById) {
        if (occupancy == null || occupancy.isEmpty()) return List.of();
        Map<String, ExistingOccupancy> byPlanningId = new LinkedHashMap<>();
        for (OptimizerOccupancy occ : occupancy) {
            OptimizerRoom room = roomsById.get(occ.classroomId());
            if (room == null) continue;
            String planningId = "occupied:" + occ.classroomId() + ":" + occ.date() + ":" + occ.startTime();
            if (byPlanningId.containsKey(planningId)) {
                log.warn("Ocupación existente duplicada descartada, planningId={}", planningId);
                continue;
            }
            OptimizerEvent event = new OptimizerEvent(planningId, null, 0,
                    occ.startTime(), occ.endTime(), Set.of(occ.date()));
            byPlanningId.put(planningId, new ExistingOccupancy(event, room));
        }
        return new ArrayList<>(byPlanningId.values());
    }

    private ScheduleSolution solve(List<OptimizerEvent> events,
                                   List<ExistingOccupancy> existing,
                                   List<OptimizerRoom> classrooms,
                                   Map<String, Set<String>> conflictsByEventId,
                                   int timeLimitSeconds) {
        List<ClassAllocation> allocations = new ArrayList<>();
        for (OptimizerEvent event : events) {
            allocations.add(new ClassAllocation(
                    event, classrooms, conflictsByEventId.getOrDefault(event.planningId(), Set.of())));
        }
        for (ExistingOccupancy occ : existing) {
            allocations.add(ClassAllocation.pinned(
                    occ.event(), occ.room(), conflictsByEventId.getOrDefault(occ.event().planningId(), Set.of())));
        }
        ScheduleSolution problem = new ScheduleSolution(classrooms, allocations, weightOverrides());
        log.info("Modelo del solver armado: {} entidades de planificación ({} nuevas + {} pinned), "
                        + "{} aulas candidatas por evento",
                allocations.size(), events.size(), existing.size(), classrooms.size());
        return runSolver(problem, timeLimitSeconds);
    }

    private ScheduleSolution runSolver(ScheduleSolution problem, int timeLimitSeconds) {
        String jobId = UUID.randomUUID().toString();
        log.info("Job del solver {} lanzado, límite {}s", jobId, timeLimitSeconds);
        SolverJob<ScheduleSolution> job = solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride()
                        .withTerminationConfig(buildTermination(timeLimitSeconds)))
                .withBestSolutionEventConsumer(event ->
                        log.info("Job del solver {}: nuevo mejor score {}", jobId, event.solution().getScore()))
                .run();
        try {
            return job.getFinalBestSolution();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.terminateEarly();
            throw new SchedulingException("Optimization interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Job del solver {} falló", jobId, cause);
            throw new SchedulingException("Error during optimization: " + cause.getMessage(), cause);
        }
    }

    private TerminationConfig buildTermination(int timeLimitSeconds) {
        TerminationConfig termination = new TerminationConfig()
                .withSecondsSpentLimit((long) timeLimitSeconds);
        long unimproved = optimizerSettings.getUnimprovedSecondsLimit();
        if (unimproved > 0) {
            termination.setUnimprovedSecondsSpentLimit(unimproved);
        }
        return termination;
    }

    private ConstraintWeightOverrides<HardMediumSoftScore> weightOverrides() {
        return ConstraintWeightOverrides.of(Map.of(
                ClassroomConstraintProvider.OVERCROWDING,
                HardMediumSoftScore.ofSoft(optimizerSettings.getOvercrowdingWeight()),
                ClassroomConstraintProvider.UNUSED_CAPACITY,
                HardMediumSoftScore.ofSoft(optimizerSettings.getUnusedCapacityWeight()),
                ClassroomConstraintProvider.SAME_ROOM_SAME_COMMISSION,
                HardMediumSoftScore.ofSoft(optimizerSettings.getSameCommissionDiffRoomWeight()),
                ClassroomConstraintProvider.SAME_BUILDING_SAME_COMMISSION,
                HardMediumSoftScore.ofSoft(optimizerSettings.getSameCommissionDiffBuildingWeight())));
    }

    private record Edge(String from, String to) {
    }

    private Map<String, Set<String>> computeConflicts(List<OptimizerEvent> events) {
        List<Edge> edges = Clashes.within(events, OptimizerEvent::occurrenceDates,
                (a, b) -> !a.planningId().equals(b.planningId()),
                (a, b, date) -> new Edge(a.planningId(), b.planningId()));

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.from(), id -> new HashSet<>()).add(edge.to());
            adjacency.computeIfAbsent(edge.to(), id -> new HashSet<>()).add(edge.from());
        }
        return adjacency;
    }
}
