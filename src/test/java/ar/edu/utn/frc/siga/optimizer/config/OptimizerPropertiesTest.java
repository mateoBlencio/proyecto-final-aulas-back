package ar.edu.utn.frc.siga.optimizer.config;

import static org.assertj.core.api.Assertions.assertThat;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OptimizerProperties")
class OptimizerPropertiesTest {

    @Test
    @DisplayName("el environment mode por defecto es NO_ASSERT")
    void defaultEnvironmentModeIsNoAssert() {
        assertThat(new OptimizerProperties().getEnvironmentMode()).isEqualTo(EnvironmentMode.NO_ASSERT);
    }
}
