package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;

public interface SysacadSyncOrchestrator {

    void sync(SysacadView view);

    SysacadResyncOutcome resyncAll();
}
