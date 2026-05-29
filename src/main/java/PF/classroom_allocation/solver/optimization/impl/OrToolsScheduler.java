package PF.classroom_allocation.solver.optimization.impl;

import PF.classroom_allocation.solver.optimization.Scheduler;

public class OrToolsScheduler implements Scheduler {

//    private final SolverConfig solverConfig;
//
//    public List<Assignment> solve(List<AcademicClass> classes, List<Room> rooms) {
//        MPSolver solver = MPSolver.createSolver("SCIP");
//        int n = classes.size(), m = rooms.size();
//
//        MPVariable[][] x = new MPVariable[n][m];
//        for (int i = 0; i < n; i++)
//            for (int j = 0; j < m; j++)
//                x[i][j] = rooms.get(j).getCapacity() < classes.get(i).getEnrolled()
//                        ? solver.makeIntVar(0, 0, "x[%d][%d]".formatted(i, j))
//                        : solver.makeIntVar(0, 1, "x[%d][%d]".formatted(i, j));
//
//        // R1: each class assigned to exactly one room
//        for (int i = 0; i < n; i++) {
//            MPConstraint r1 = solver.makeConstraint(1, 1, "assign_" + i);
//            for (int j = 0; j < m; j++) r1.setCoefficient(x[i][j], 1);
//        }
//
//        // R3: overlapping classes cannot share a room
//        for (int i = 0; i < n; i++)
//            for (int i2 = i + 1; i2 < n; i2++)
//                if (OverlapDetector.overlaps(classes.get(i), classes.get(i2)))
//                    for (int j = 0; j < m; j++) {
//                        MPConstraint r3 = solver.makeConstraint(0, 1, "conflict_%d_%d_%d".formatted(i, i2, j));
//                        r3.setCoefficient(x[i][j], 1);
//                        r3.setCoefficient(x[i2][j], 1);
//                    }
//
//        // Objective: minimize idle capacity
//        MPObjective obj = solver.objective();
//        for (int i = 0; i < n; i++)
//            for (int j = 0; j < m; j++)
//                obj.setCoefficient(x[i][j], rooms.get(j).getCapacity() - classes.get(i).getEnrolled());
//        obj.setMinimization();
//
//        solver.setTimeLimit(solverConfig.getTimeLimitMs());
//        MPSolver.ResultStatus status = solver.solve();
//
//        if (status == MPSolver.ResultStatus.INFEASIBLE)
//            throw new InfeasibleScheduleException("No feasible assignment found");
//
//        List<Assignment> result = new ArrayList<>();
//        for (int i = 0; i < n; i++)
//            for (int j = 0; j < m; j++)
//                if (x[i][j].solutionValue() > 0.5)
//                    result.add(new Assignment(classes.get(i), rooms.get(j)));
//        return result;
//    }

}
