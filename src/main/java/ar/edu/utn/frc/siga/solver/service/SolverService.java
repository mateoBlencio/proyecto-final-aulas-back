package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.model.OccupancyDto;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public interface SolverService {

    /**
     * Corre el solver sobre los eventos dados, usando las aulas provistas y respetando
     * la ocupación existente (no-solapamiento duro). Guarda y devuelve la preview.
     */
    SolverPreview preview(List<SolverEvent> events, List<SolverRoom> classrooms,
                          List<OccupancyDto> occupancy, int timeLimitSeconds);

    /** Recupera una preview guardada; lanza PreviewNotFoundException (410) si no existe/expiró. */
    SolverPreview getPreview(String previewId);
}
