package ar.edu.utn.frc.siga.sysacad.internal.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import java.time.Duration;
import java.time.LocalTime;
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
    @DisplayName("syncCron arma el cron de Spring para domingo 03:00")
    void syncCronSundayThreeAm() {
        stubSync("SUN", LocalTime.of(3, 0));

        assertThat(settings.syncCron()).isEqualTo("0 0 3 * * SUN");
    }

    @Test
    @DisplayName("syncCron respeta minutos y hora de dos dígitos")
    void syncCronWednesdayFourteenThirty() {
        stubSync("WED", LocalTime.of(14, 30));

        assertThat(settings.syncCron()).isEqualTo("0 30 14 * * WED");
    }

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
        when(reader.getString(SettingKey.SYSACAD_WRITE_MODE)).thenReturn("DRY_RUN");

        assertThat(settings.writeMode()).isEqualTo(SysacadWriteMode.DRY_RUN);
    }

    @Test
    @DisplayName("commissionMessageMode parsea el string del setting de autogestión")
    void commissionMessageModeParsesString() {
        when(reader.getString(SettingKey.SYSACAD_AUTOGESTION_MODE)).thenReturn("TEST_COMMISSIONS");

        assertThat(settings.commissionMessageMode()).isEqualTo(CommissionMessageMode.TEST_COMMISSIONS);
    }

    @Test
    @DisplayName("Un modo desconocido falla en lugar de caer a un default")
    void unknownModeFails() {
        when(reader.getString(SettingKey.SYSACAD_WRITE_MODE)).thenReturn("MAYBE");

        assertThatThrownBy(settings::writeMode).isInstanceOf(IllegalArgumentException.class);
    }

    private void stubSync(String day, LocalTime time) {
        when(reader.getString(SettingKey.SYSACAD_SYNC_DAY_OF_WEEK)).thenReturn(day);
        when(reader.getTime(SettingKey.SYSACAD_SYNC_TIME)).thenReturn(time);
    }
}
