package ar.edu.utn.frc.siga.settings.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettingKey")
class SettingKeyTest {

    @ParameterizedTest
    @EnumSource(SettingKey.class)
    @DisplayName("toda constante tiene label no vacío, distinto de su clave técnica y de su nombre de constante")
    void everyConstantHasReadableLabel(SettingKey key) {
        assertThat(key.getLabel()).isNotBlank().isNotEqualTo(key.getKey()).isNotEqualTo(key.name());
    }

    @Test
    @DisplayName("no hay dos constantes con el mismo label: el registro no distinguiría una configuración de otra")
    void labelsAreUnique() {
        Set<String> labels = Arrays.stream(SettingKey.values()).map(SettingKey::getLabel)
                .collect(Collectors.toSet());

        assertThat(labels).hasSize(SettingKey.values().length);
    }
}
