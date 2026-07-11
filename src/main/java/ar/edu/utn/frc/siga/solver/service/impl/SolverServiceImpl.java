package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.solver.exception.SchedulingException;
import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.PreviewStore;
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
public class SolverServiceImpl implements SolverService {

    private final SolverManager<ScheduleSolution> solverManager;
    private final SolverProperties solverProperties;
    private final PreviewStore previewStore;

    /** Ocupación existente ya resuelta a un aula concreta del conjunto de candidatas. */
    private record ExistingOccupancy(SolverEvent event, SolverRoom room) {
    }

    @Override
    public SolverPreview preview(List<SolverEvent> events, List<SolverRoom> classrooms,
                                 List<SolverOccupancy> occupancy, int timeLimitSeconds) {
        Map<Integer, SolverRoom> roomsById = classrooms.stream()
                .collect(Collectors.toMap(SolverRoom::id, r -> r));
        List<ExistingOccupancy> existing = buildExistingOccupancy(occupancy, roomsById);

        // Conflictos calculados sobre nuevos + existentes → adyacencia simétrica que
        // permite que el noOverlap bloquee un aula ocupada para los eventos nuevos.
        List<SolverEvent> allEvents = new ArrayList<>(events);
        existing.forEach(e -> allEvents.add(e.event()));
        Map<String, Set<String>> conflictsByEventId = computeConflicts(allEvents);

        log.info("Starting solver preview: {} events, {} classrooms, {} occupied slots, limit {}s",
                events.size(), classrooms.size(), existing.size(), timeLimitSeconds);

        long start = System.currentTimeMillis();
        ScheduleSolution solution = solve(events, existing, classrooms, conflictsByEventId, timeLimitSeconds);
        log.info("Solver preview completed in {}ms, score {}",
                System.currentTimeMillis() - start, solution.getScore());

        SolverPreview preview = toPreview(solution);
        previewStore.save(preview);
        return preview;
    }

    @Override
    public SolverPreview getPreview(String previewId) {
        return previewStore.get(previewId)
                .orElseThrow(() -> new ExpiredPreviewException(previewId));
    }

    @Override
    public void invalidatePreview(String previewId) {
        previewStore.remove(previewId);
    }

    private SolverPreview toPreview(ScheduleSolution solution) {
        List<SolverAllocation> allocations = solution.getAllocations().stream()
                .filter(a -> !a.isPinned())
                .map(a -> new SolverAllocation(
                        a.getEvent().planningId(),
                        a.getClassroom() != null ? a.getClassroom().id() : null))
                .toList();
        String previewId = "prev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new SolverPreview(previewId, allocations);
    }

    /**
     * Cada ocupación existente cuya aula sea candidata se vuelve una asignación pinned.
     * Si el aula ocupada no es candidata (no disponible), no puede colisionar con ningún
     * evento nuevo y se descarta.
     * Deduplicada por planningId: si en BD hay dos allocations preexistentes conflictivas
     * (mismo aula/fecha/hora), generarían el mismo {@code @PlanningId} y Timefold explota.
     * Se conserva solo la primera y se loguea la duplicada descartada.
     */
    private List<ExistingOccupancy> buildExistingOccupancy(List<SolverOccupancy> occupancy,
                                                           Map<Integer, SolverRoom> roomsById) {
        if (occupancy == null || occupancy.isEmpty()) return List.of();
        Map<String, ExistingOccupancy> byPlanningId = new LinkedHashMap<>();
        for (SolverOccupancy occ : occupancy) {
            SolverRoom room = roomsById.get(occ.classroomId());
            if (room == null) continue;
            String planningId = "occupied:" + occ.classroomId() + ":" + occ.date() + ":" + occ.startTime();
            if (byPlanningId.containsKey(planningId)) {
                log.warn("Ocupación existente duplicada descartada, planningId={}", planningId);
                continue;
            }
            SolverEvent event = new SolverEvent(planningId, null, 0,
                    occ.startTime(), occ.endTime(), Set.of(occ.date()));
            byPlanningId.put(planningId, new ExistingOccupancy(event, room));
        }
        return new ArrayList<>(byPlanningId.values());
    }

    private ScheduleSolution solve(List<SolverEvent> events,
                                   List<ExistingOccupancy> existing,
                                   List<SolverRoom> classrooms,
                                   Map<String, Set<String>> conflictsByEventId,
                                   int timeLimitSeconds) {
        List<ClassAllocation> allocations = new ArrayList<>();
        for (SolverEvent event : events) {
            allocations.add(new ClassAllocation(
                    event, classrooms, conflictsByEventId.getOrDefault(event.planningId(), Set.of())));
        }
        for (ExistingOccupancy occ : existing) {
            allocations.add(ClassAllocation.pinned(
                    occ.event(), occ.room(), conflictsByEventId.getOrDefault(occ.event().planningId(), Set.of())));
        }
        ScheduleSolution problem = new ScheduleSolution(classrooms, allocations);
        return runSolver(problem, timeLimitSeconds);
    }

    private ScheduleSolution runSolver(ScheduleSolution problem, int timeLimitSeconds) {
        String jobId = UUID.randomUUID().toString();
        SolverJob<ScheduleSolution> job = solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride()
                        .withTerminationConfig(buildTermination(timeLimitSeconds)))
                .run();
        try {
            return job.getFinalBestSolution();
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
     * Construye la adyacencia de conflictos horarios (eventId → eventIds que solapan)
     * agrupando por fecha de ocurrencia y barriendo ordenado por hora de inicio dentro
     * de cada fecha. Evita el producto cartesiano; el Set de cada evento deduplica los
     * pares hallados en múltiples fechas compartidas.
     */
    private Map<String, Set<String>> computeConflicts(List<SolverEvent> events) {
        Map<LocalDate, List<SolverEvent>> eventsByDate = new HashMap<>();
        for (SolverEvent event : events) {
            for (LocalDate date : event.occurrenceDates()) {
                eventsByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(event);
            }
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (List<SolverEvent> sameDate : eventsByDate.values()) {
            if (sameDate.size() < 2) continue;
            sameDate.sort(Comparator.comparing(SolverEvent::startTime));
            for (int i = 0; i < sameDate.size(); i++) {
                SolverEvent a = sameDate.get(i);
                for (int j = i + 1; j < sameDate.size(); j++) {
                    SolverEvent b = sameDate.get(j);
                    if (!b.startTime().isBefore(a.endTime())) break;
                    if (a.planningId().equals(b.planningId())) continue;
                    if (timesOverlap(a, b)) {
                        adjacency.computeIfAbsent(a.planningId(), id -> new HashSet<>()).add(b.planningId());
                        adjacency.computeIfAbsent(b.planningId(), id -> new HashSet<>()).add(a.planningId());
                    }
                }
            }
        }
        return adjacency;
    }

    private boolean timesOverlap(SolverEvent a, SolverEvent b) {
        return a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
    }
}