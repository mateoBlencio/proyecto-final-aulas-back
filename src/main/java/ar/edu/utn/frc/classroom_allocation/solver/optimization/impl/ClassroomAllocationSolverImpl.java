package ar.edu.utn.frc.classroom_allocation.solver.optimization.impl;

import ar.edu.utn.frc.classroom_allocation.solver.exception.SchedulingException;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.solver.model.Event;
import ar.edu.utn.frc.classroom_allocation.solver.optimization.ClassroomAllocationSolver;
import ar.edu.utn.frc.classroom_allocation.solver.model.ConflictPair;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClassroomAllocationSolverImpl implements ClassroomAllocationSolver {

    @Override
    public ScheduleSolution solve(List<Event> events,
                                  List<Classroom> classrooms,
                                  Set<ConflictPair> conflicts,
                                  Map<String, List<Classroom>> candidatesByEventId,
                                  int timeLimitSeconds) {
        ScheduleSolution problem = buildProblem(events, classrooms, conflicts, candidatesByEventId);
        return runSolver(problem, timeLimitSeconds);
    }

    private ScheduleSolution buildProblem(List<Event> events,
                                          List<Classroom> classrooms,
                                          Set<ConflictPair> conflicts,
                                          Map<String, List<Classroom>> candidatesByEventId) {
        List<ClassAssignment> assignments = events.stream()
                .map(event -> buildAssignment(event, classrooms, conflicts, candidatesByEventId))
                .toList();
        return new ScheduleSolution(classrooms, assignments, null);
    }

    private ClassAssignment buildAssignment(Event event,
                                            List<Classroom> classrooms,
                                            Set<ConflictPair> conflicts,
                                            Map<String, List<Classroom>> candidatesByEventId) {
        Set<String> conflictingIds = conflicts.stream()
                .filter(p -> p.involves(event.getId()))
                .map(p -> p.otherEventId(event.getId()))
                .collect(Collectors.toSet());

        List<Classroom> candidates = candidatesByEventId.getOrDefault(event.getId(), classrooms);

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
}
