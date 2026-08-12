package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.config.OptimizerProperties;
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

/**
 * Orquesta una corrida del solver: arma el modelo de planificación (eventos nuevos + ocupación
 * existente pinned), lo somete al {@link SolverManager} de Timefold con límite de tiempo, y
 * devuelve el resultado. No persiste nada: guardar/recuperar/invalidar la preview es
 * responsabilidad del caller (módulo {@code preview}, dueño del {@code PreviewStore}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizerServiceImpl implements OptimizerService {

    private final SolverManager<ScheduleSolution> solverManager;
    private final OptimizerProperties optimizerProperties;

    /** Ocupación existente ya resuelta a un aula concreta del conjunto de candidatas. */
    private record ExistingOccupancy(OptimizerEvent event, OptimizerRoom room) {
    }

    @Override
    public OptimizationResult optimize(List<OptimizerEvent> events, List<OptimizerRoom> classrooms,
                                 List<OptimizerOccupancy> occupancy, int timeLimitSeconds) {
        Map<Integer, OptimizerRoom> roomsById = classrooms.stream()
                .collect(Collectors.toMap(OptimizerRoom::id, r -> r));
        List<ExistingOccupancy> existing = buildExistingOccupancy(occupancy, roomsById);

        // Conflictos calculados sobre nuevos + existentes → adyacencia simétrica que
        // permite que el noOverlap bloquee un aula ocupada para los eventos nuevos.
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

    /**
     * Cada ocupación existente cuya aula sea candidata se vuelve una asignación pinned.
     * Si el aula ocupada no es candidata (no disponible), no puede colisionar con ningún
     * evento nuevo y se descarta.
     * Deduplicada por planningId: si en BD hay dos allocations preexistentes conflictivas
     * (mismo aula/fecha/hora), generarían el mismo {@code @PlanningId} y Timefold explota.
     * Se conserva solo la primera y se loguea la duplicada descartada.
     */
    private List<ExistingOccupancy> buildExistingOccupancy(List<OptimizerOccupancy> occupancy,
                                                           Map<Integer, OptimizerRoom> roomsById) {
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
        ScheduleSolution problem = new ScheduleSolution(classrooms, allocations);
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
        long unimproved = optimizerProperties.getUnimprovedSecondsLimit();
        if (unimproved > 0) {
            termination.setUnimprovedSecondsSpentLimit(unimproved);
        }
        return termination;
    }

    /** Un par de eventos que comparten fecha y cuyas franjas se pisan. */
    private record Edge(String from, String to) {
    }

    /**
     * Construye la adyacencia de conflictos horarios (eventId → eventIds que solapan) vía
     * {@link Clashes#within}, que agrupa por fecha de ocurrencia y barre ordenado por hora
     * de inicio con corte temprano. El Set de cada evento deduplica los pares hallados en
     * múltiples fechas compartidas.
     */
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
