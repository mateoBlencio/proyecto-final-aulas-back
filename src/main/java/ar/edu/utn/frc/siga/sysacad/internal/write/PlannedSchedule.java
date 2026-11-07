package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;

public record PlannedSchedule(SysacadSchedule schedule, NextClassDecision decision) {
}
