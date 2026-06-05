package PF.classroom_allocation.solver.optimization;

import PF.classroom_allocation.solver.model.Classroom;
import PF.classroom_allocation.solver.model.Event;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SolverInput {

    // Eventos a asignar
    List<Event> events;

    // Aulas disponibles
    List<Classroom> classrooms;

    // Pares de eventos que no pueden compartir aula (precalculado)
    Set<ConflictPair> conflicts;

    // Aulas candidatas por evento (resultado de la poda previa)
    Map<String, List<Classroom>> candidatesByEventId;
}