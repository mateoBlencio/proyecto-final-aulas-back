package ar.edu.utn.frc.siga.sysacad.internal.config;

public enum SysacadWriteMode {
    OFF, DRY_RUN, SANDBOX, ON;

    public boolean sendsToSysacad() {
        return this == ON;
    }

    public boolean keepsSigaChanges() {
        return this == SANDBOX || this == ON;
    }
}
