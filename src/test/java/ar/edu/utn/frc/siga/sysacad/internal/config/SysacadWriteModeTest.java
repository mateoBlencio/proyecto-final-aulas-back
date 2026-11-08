package ar.edu.utn.frc.siga.sysacad.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SysacadWriteMode")
class SysacadWriteModeTest {

    @ParameterizedTest
    @CsvSource({"OFF,false", "DRY_RUN,false", "SANDBOX,true", "ON,true"})
    @DisplayName("keepsSigaChanges: solo SANDBOX y ON conservan en SIGA los cambios de aula")
    void keepsSigaChanges(SysacadWriteMode mode, boolean expected) {
        assertThat(mode.keepsSigaChanges()).isEqualTo(expected);
    }
}
