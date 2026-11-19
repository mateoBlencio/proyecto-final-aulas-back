package ar.edu.utn.frc.siga.optimizer.service.impl;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerConfiguration;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerProperties;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerSettings;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ocupaciones existentes con el solver real")
class OptimizerOccupancySolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    private static SolverManager<ScheduleSolution> solverManager;
    private static OptimizerServiceImpl service;

    @BeforeAll
    static void setUp() {
        OptimizerSettings settings = new OptimizerSettings() {
            @Override
            public long getUnimprovedSecondsLimit() {
                return 0;
            }

            @Override
            public long getSolverSecondsSpentLimit() {
                return 5;
            }

            @Override
            public int getWeight(SettingKey key) {
                return 1000;
            }
        };
        OptimizerProperties properties = new OptimizerProperties();
        properties.setParallelSolverCount("1");
        properties.setEnvironmentMode(EnvironmentMode.PHASE_ASSERT);
        OptimizerConfiguration configuration = new OptimizerConfiguration();
        solverManager = configuration.scheduleSolverManager(
                configuration.scheduleSolverFactory(properties, settings), properties);
        service = new OptimizerServiceImpl(solverManager, settings);
    }

    @AfterAll
    static void tearDown() {
        solverManager.close();
    }

    private static OptimizerEvent eventAt(String id, LocalTime start, LocalTime end) {
        return new OptimizerEvent(id, null, 10, start, end, Set.of(DATE));
    }

    @Test
    @DisplayName("dos ocupaciones con misma aula, fecha e inicio y distinto fin: el evento no entra en la franja más larga")
    void sameStartDifferentEnd_longerOccupancyStillBlocks() {
        OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);
        List<OptimizerOccupancy> occupancy = List.of(
                new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(12, 0)));

        OptimizationResult result = service.optimize(
                List.of(eventAt("e", LocalTime.of(10, 0), LocalTime.of(11, 0))), List.of(room), occupancy, 2);

        assertThat(result.allocations()).singleElement()
                .satisfies(allocation -> assertThat(allocation.classroomId()).isNull());
    }

    @Test
    @DisplayName("control: con solo la ocupación corta, el evento posterior sí recibe el aula")
    void onlyShortOccupancy_eventGetsRoom() {
        OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);
        List<OptimizerOccupancy> occupancy = List.of(
                new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(9, 0)));

        OptimizationResult result = service.optimize(
                List.of(eventAt("e", LocalTime.of(10, 0), LocalTime.of(11, 0))), List.of(room), occupancy, 2);

        assertThat(result.allocations().getFirst().classroomId()).isEqualTo(1L);
    }
}
