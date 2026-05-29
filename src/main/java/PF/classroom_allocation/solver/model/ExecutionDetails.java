package PF.classroom_allocation.solver.model;

import com.google.ortools.linearsolver.MPSolver;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExecutionDetails {

    LocalDateTime startTime;
    LocalDateTime endTime;
    MPSolver.ResultStatus resultStatus;

}
