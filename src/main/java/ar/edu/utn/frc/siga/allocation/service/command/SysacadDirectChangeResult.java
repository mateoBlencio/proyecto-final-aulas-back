package ar.edu.utn.frc.siga.allocation.service.command;

import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import java.util.List;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface SysacadDirectChangeResult {

    @NamedInterface("api")
    record Applied(List<ClassroomMovements.Change> changes) implements SysacadDirectChangeResult {}

    @NamedInterface("api")
    record Occupied(List<Long> occurrenceIds) implements SysacadDirectChangeResult {}
}
