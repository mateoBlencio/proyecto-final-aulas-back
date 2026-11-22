package ar.edu.utn.frc.siga.sysacad.api;

import java.time.LocalDate;

public record SysacadWritePreviewItemDto(
        Long scheduleId,
        String courseCode,
        Integer subject,
        Integer commission,
        Integer dayOfWeek,
        Integer semester,
        Integer knownBuilding,
        Integer knownRoom,
        String decision,
        Integer targetBuilding,
        Integer targetRoom,
        Long occurrenceId,
        LocalDate date,
        Long blockingItemId,
        String reason
) {}
