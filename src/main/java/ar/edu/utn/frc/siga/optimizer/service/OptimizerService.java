package ar.edu.utn.frc.siga.optimizer.service;

import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public interface OptimizerService {

    /**
     * Corre el solver sobre los eventos dados, usando las aulas provistas y respetando
     * la ocupación existente (no-solapamiento duro). Calcula y devuelve el resultado; no lo
     * persiste — eso es responsabilidad del caller.
     */
    OptimizationResult optimize(List<OptimizerEvent> events, List<OptimizerRoom> classrooms,
                          List<OptimizerOccupancy> occupancy, int timeLimitSeconds);
}
