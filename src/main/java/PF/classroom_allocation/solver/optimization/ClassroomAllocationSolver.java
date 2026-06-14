package PF.classroom_allocation.solver.optimization;

import PF.classroom_allocation.solver.model.Classroom;
import PF.classroom_allocation.solver.model.ConflictPair;
import PF.classroom_allocation.solver.model.Event;
import PF.classroom_allocation.solver.optimization.impl.ScheduleSolution;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ClassroomAllocationSolver {

    ScheduleSolution solve(
            List<Event> events,
            List<Classroom> classrooms,
            Set<ConflictPair> conflicts,
            Map<String, List<Classroom>> candidatesByEventId,
            int timeLimitSeconds
    );
}
