package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;

/**
 * El modo vive en la tabla de configuración, compartida entre contextos: cada test lo fija antes y lo
 * devuelve a OFF después para no contaminar a las clases que arrancan un contexto nuevo.
 */
public final class SysacadModeSupport {

    private SysacadModeSupport() {
    }

    public static void use(SettingsStore store, SysacadWriteMode mode) {
        store.write(SettingKey.SYSACAD_WRITE_MODE, mode.name());
        store.write(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES, "1440");
    }

    public static void reset(SettingsStore store) {
        store.write(SettingKey.SYSACAD_WRITE_MODE, SysacadWriteMode.OFF.name());
    }
}
