package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.config.SolverConfiguration;
import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.exception.PreviewNotFoundException;
import ar.edu.utn.frc.siga.solver.model.OccupancyDto;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverAssignment;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.impl.CaffeinePreviewStore;
import ar.edu.utn.frc.siga.solver.service.impl.SolverServiceImpl;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el núcleo del rediseño: la ocupación existente (pinned) bloquea al evento
 * nuevo de usar el aula ocupada un día que solapa, y que el preview queda persistido.
 */
class SolverPinnedOccupancyTest {

    private SolverServiceImpl newService() {
        SolverConfiguration config = new SolverConfiguration();
        SolverProperties props = new SolverProperties();
        props.setUnimprovedSecondsLimit(1);
        SolverFactory<ScheduleSolution> factory = config.scheduleSolverFactory(props);
        SolverManager<ScheduleSolution> manager = config.scheduleSolverManager(factory, props);
        return new SolverServiceImpl(manager, props, new CaffeinePreviewStore(props));
    }

    private SolverRoom room(int id) {
        return new SolverRoom(id, 40, 1);
    }

    // Evento recurrente, lunes 08:00-09:30, 3 lunes: 2026-03-02, 09, 16.
    private SolverEvent mondayEvent() {
        return new SolverEvent("e1", "1K1", 30, LocalTime.of(8, 0), LocalTime.of(9, 30),
                Set.of(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16)));
    }

    @Test
    void newEvent_avoidsClassroomOccupiedOnOverlappingDate() {
        // Aula 1 ocupada el lunes 2026-03-09, 08:00-09:30 → colisiona con e1 ese día.
        List<OccupancyDto> occupancy = List.of(
                new OccupancyDto(1, LocalDate.of(2026, 3, 9), LocalTime.of(8, 0), LocalTime.of(9, 30)));

        SolverServiceImpl service = newService();
        SolverPreview preview = service.preview(List.of(mondayEvent()), List.of(room(1), room(2)), occupancy, 5);

        assertThat(preview.assignments()).hasSize(1);
        SolverAssignment assignment = preview.assignments().get(0);
        assertThat(assignment.eventId()).isEqualTo("e1");
        assertThat(assignment.classroomId()).isEqualTo(2);

        // Preview persistida y recuperable; id inexistente → 410.
        assertThat(service.getPreview(preview.previewId()).previewId()).isEqualTo(preview.previewId());
        assertThatThrownBy(() -> service.getPreview("prev_missing"))
                .isInstanceOf(PreviewNotFoundException.class);
    }
}
