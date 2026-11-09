package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.sysacad.internal.config.CommissionMessageMode;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

public final class SysacadTestSettings {

    private SysacadTestSettings() {
    }

    public static SysacadRuntimeSettings with(SysacadWriteMode mode) {
        SysacadRuntimeSettings settings = mock(SysacadRuntimeSettings.class);
        lenient().when(settings.writeMode()).thenReturn(mode);
        lenient().when(settings.commissionMessageMode()).thenReturn(CommissionMessageMode.OFF);
        lenient().when(settings.jobDelay()).thenReturn(Duration.ofMinutes(5));
        lenient().when(settings.viewReadInterval()).thenReturn(Duration.ofHours(1));
        return settings;
    }
}
