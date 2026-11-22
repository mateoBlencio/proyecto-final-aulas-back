package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;

public interface SysacadSyncOrchestrator {

    SysacadResyncOutcome resyncAll();
}
