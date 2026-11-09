package ar.edu.utn.frc.siga.sysacad.internal.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SysacadRuntimeSettings")
class SysacadRuntimeSettingsTest {

    private final SettingsReader reader = mock(SettingsReader.class);
    private final SysacadRuntimeSettings settings = new SysacadRuntimeSettings(reader);

    @Test
    @DisplayName("jobDelay y viewReadInterval se interpretan en minutos")
    void delaysAreMinutes() {
        when(reader.getLong(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES)).thenReturn(7L);
        when(reader.getLong(SettingKey.SYSACAD_WRITE_VIEW_READ_INTERVAL_MINUTES)).thenReturn(90L);

        assertThat(settings.jobDelay()).isEqualTo(Duration.ofMinutes(7));
        assertThat(settings.viewReadInterval()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    @DisplayName("writeMode parsea el string del setting")
    void writeModeParsesString() {
        when(reader.getString(SettingKey.SYSACAD_WRITE_MODE)).thenReturn("SANDBOX");

        assertThat(settings.writeMode()).isEqualTo(SysacadWriteMode.SANDBOX);
    }

    @Test
    @DisplayName("commissionMessageMode parsea el string del setting de autogestión")
    void commissionMessageModeParsesString() {
        when(reader.getString(SettingKey.SYSACAD_AUTOGESTION_MODE)).thenReturn("ON");

        assertThat(settings.commissionMessageMode()).isEqualTo(CommissionMessageMode.ON);
    }

    @Test
    @DisplayName("Un modo desconocido falla en lugar de caer a un default")
    void unknownModeFails() {
        when(reader.getString(SettingKey.SYSACAD_WRITE_MODE)).thenReturn("MAYBE");

        assertThatThrownBy(settings::writeMode).isInstanceOf(IllegalArgumentException.class);
    }
}
