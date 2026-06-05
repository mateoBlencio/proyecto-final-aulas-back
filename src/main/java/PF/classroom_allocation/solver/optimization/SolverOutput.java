package PF.classroom_allocation.solver.optimization;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SolverOutput {

    List<Assignment> assignments;
    int hardScore;
    int softScore;

    public record Assignment(
            String eventId,
            String classroomId,
            int overcrowding,
            int unusedCapacity
    ) {}
}
