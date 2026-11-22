package ar.edu.utn.frc.siga.optimizer.service.impl;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerConfiguration;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerProperties;
import ar.edu.utn.frc.siga.optimizer.config.OptimizerSettings;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Requisitos fisicos con el solver real")
class OptimizerRequirementsSolverTest {

    private static final Map<SettingKey, Integer> WEIGHTS = Map.of(
            SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, 100000,
            SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_ROOM, 2000,
            SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING, 4000,
            SettingKey.OPTIMIZER_WEIGHT_SAME_EVENT_DIFF_BUILDING, 8000,
            SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY, 1,
            SettingKey.OPTIMIZER_WEIGHT_MISSING_PROJECTOR, 10000,
            SettingKey.OPTIMIZER_WEIGHT_MISSING_COMPUTER, 20000);

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
                return WEIGHTS.get(key);
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

    private static OptimizerEvent event(int enrolled, boolean needsProjector, int computersNeeded) {
        return new OptimizerEvent("e", null, enrolled, LocalTime.of(8, 0), LocalTime.of(10, 0),
                Set.of(LocalDate.of(2026, 3, 2)), Set.of(), null, needsProjector, computersNeeded);
    }

    private static OptimizerRoom room(long id, int capacity, boolean hasProjector, int computers) {
        return new OptimizerRoom(id, capacity, 100L, true, Set.of(), hasProjector, computers);
    }

    private static Long chosenRoom(OptimizationResult result) {
        return result.allocations().getFirst().classroomId();
    }

    @Test
    @DisplayName("con dos aulas iguales elige la que tiene proyector")
    void prefersRoomWithProjector() {
        OptimizationResult result = service.optimize(List.of(event(30, true, 0)),
                List.of(room(1L, 40, false, 0), room(2L, 40, true, 0)), List.of(), 2);

        assertThat(chosenRoom(result)).isEqualTo(2L);
    }

    @Test
    @DisplayName("el aula con proyector pierde si excede la capacidad por una persona")
    void overcrowdingBeatsProjector() {
        OptimizationResult result = service.optimize(List.of(event(30, true, 0)),
                List.of(room(1L, 30, false, 0), room(2L, 29, true, 0)), List.of(), 2);

        assertThat(chosenRoom(result)).isEqualTo(1L);
    }

    @Test
    @DisplayName("con dos aulas iguales elige la que tiene computadoras suficientes")
    void prefersRoomWithComputers() {
        OptimizationResult result = service.optimize(List.of(event(20, false, 20)),
                List.of(room(1L, 30, false, 5), room(2L, 30, false, 25)), List.of(), 2);

        assertThat(chosenRoom(result)).isEqualTo(2L);
    }

    @Test
    @DisplayName("sin el requisito en el evento no se prefiere el aula con proyector")
    void projectorIgnoredWhenNotRequested() {
        OptimizationResult result = service.optimize(List.of(event(30, false, 0)),
                List.of(room(1L, 30, false, 0), room(2L, 100, true, 0)), List.of(), 2);

        assertThat(chosenRoom(result)).isEqualTo(1L);
    }
}
