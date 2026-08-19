package ar.edu.utn.frc.siga.sysacad.api;

import java.time.Instant;

public record SysacadSyncStateDto(
        SysacadView view,
        Instant lastSuccessAt,
        Integer lastRowsAffected,
        String lastError,
        Instant lastErrorAt,
        Instant updatedAt
) {}
