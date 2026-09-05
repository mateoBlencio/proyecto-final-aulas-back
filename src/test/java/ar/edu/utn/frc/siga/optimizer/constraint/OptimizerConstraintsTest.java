package ar.edu.utn.frc.siga.optimizer.constraint;

import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OptimizerConstraints")
class OptimizerConstraintsTest {

    private static final Set<String> EXPECTED_NAMES = Set.of(
            "Sin solapamiento",
            "Asignar todo lo posible",
            "Minimizar sobreocupacion",
            "Minimizar subocupacion",
            "Preferir misma aula por comision",
            "Preferir mismo edificio por comision");

    private static final Set<SettingKey> EXPECTED_WEIGHT_KEYS = Set.of(
            SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING,
            SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY,
            SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_ROOM,
            SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING);

    @Test
    @DisplayName("discovers exactly the six constraints via ServiceLoader")
    void discoversAllConstraints() {
        List<OptimizerConstraint> constraints = OptimizerConstraints.all();

        assertThat(constraints).hasSize(6);
    }

    @Test
    @DisplayName("names match the expected set exactly")
    void namesMatchExpected() {
        Set<String> names = OptimizerConstraints.all().stream()
                .map(OptimizerConstraint::name)
                .collect(Collectors.toSet());

        assertThat(names).isEqualTo(EXPECTED_NAMES);
    }

    @Test
    @DisplayName("exactly the four soft constraints declare a weight key")
    void weightKeysMatchExpected() {
        List<OptimizerConstraint> withWeight = OptimizerConstraints.all().stream()
                .filter(c -> c.weightKey().isPresent())
                .toList();

        assertThat(withWeight).hasSize(4);

        Set<SettingKey> weightKeys = withWeight.stream()
                .map(c -> c.weightKey().orElseThrow())
                .collect(Collectors.toSet());
        assertThat(weightKeys).isEqualTo(EXPECTED_WEIGHT_KEYS);

        List<OptimizerConstraint> withoutWeight = OptimizerConstraints.all().stream()
                .filter(c -> c.weightKey().isEmpty())
                .toList();
        Set<String> namesWithoutWeight = withoutWeight.stream()
                .map(OptimizerConstraint::name)
                .collect(Collectors.toSet());
        assertThat(namesWithoutWeight).isEqualTo(Set.of("Sin solapamiento", "Asignar todo lo posible"));
    }
}
