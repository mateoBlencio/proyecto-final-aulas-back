package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
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
                          List<SolverOccupancy> occupancy, int timeLimitSeconds);

    /** Recupera una preview guardada; lanza PreviewNotFoundException (410) si no existe/expiró. */
    SolverPreview getPreview(String previewId);

    /**
     * Invalida una preview ya aplicada (confirmada). Se llama al final del confirm, tras
     * persistir: un re-confirm sobre el mismo previewId da 410 (protección natural contra
     * doble submit) en vez de volver a aplicar la misma propuesta.
     */
    void invalidatePreview(String previewId);
}
