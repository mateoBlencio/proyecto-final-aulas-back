package PF.classroom_allocation.solver.optimization.impl;

import PF.classroom_allocation.solver.model.Classroom;
import PF.classroom_allocation.solver.model.Event;
import PF.classroom_allocation.solver.optimization.ClassroomAllocationSolver;
import PF.classroom_allocation.solver.optimization.SolverInput;
import PF.classroom_allocation.solver.optimization.SolverOutput;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassroomAllocationSolverImpl implements ClassroomAllocationSolver {

    final SolverManager<ScheduleSolution> solverManager;
    final AtomicLong jobIdCounter = new AtomicLong();

    @Override
    public SolverOutput solve(SolverInput input) {
        ScheduleSolution problem  = buildProblem(input);
        ScheduleSolution solution = runSolver(problem);
        return mapToOutput(solution);
    }

    private ScheduleSolution buildProblem(SolverInput input) {
        List<ClassAssignment> assignments = input.getEvents().stream()
                .map(event -> buildAssignment(event, input)).toList();

        return new ScheduleSolution(input.getClassrooms(), assignments, null);
    }

    private ClassAssignment buildAssignment(Event event, SolverInput input) {
        Set<String> conflictingIds = input.getConflicts().stream()
                .filter(p -> p.involves(event.getId()))
                .map(p -> p.otherEventId(event.getId()))
                .collect(Collectors.toSet());

        List<Classroom> candidates = input.getCandidatesByEventId()
                .getOrDefault(event.getId(), input.getClassrooms());

        return new ClassAssignment(event, candidates, conflictingIds);
    }

    private ScheduleSolution runSolver(ScheduleSolution problem) {
        SolverJob<ScheduleSolution> job =
                solverManager.solve(jobIdCounter.incrementAndGet(), problem);
        try {
            return job.getFinalBestSolution();
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error during optimization", e);
        }
    }

    private SolverOutput mapToOutput(ScheduleSolution solution) {
        List<SolverOutput.Assignment> assignments = solution.getAssignments().stream()
                .map(a -> new SolverOutput.Assignment(
                        a.getEvent().getId(),
                        a.getClassroom() != null ? a.getClassroom().getId() : null,
                        a.getOvercrowding(),
                        a.getUnusedCapacity()
                ))
                .toList();

        var score = solution.getScore();
        return SolverOutput.builder()
                .assignments(assignments)
                .hardScore(score != null ? (int) score.hardScore() : Integer.MIN_VALUE)
                .softScore(score != null ? (int) score.softScore() : Integer.MIN_VALUE)
                .build();
    }
}