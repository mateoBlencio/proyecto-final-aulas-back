package ar.edu.utn.frc.siga.sysacad.api;

import java.time.Instant;

public record SysacadWriteStatusDto(
        String mode,
        Instant lastRunStartedAt,
        Instant lastRunFinishedAt,
        String lastResult,
        Instant lastViewReadAt,
        Instant outageSince,
        Instant pausedUntil,
        String lastError,
        long reviewCount,
        long waitingCount
) {}
