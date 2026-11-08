package ar.edu.utn.frc.siga.sysacad.api;

import java.time.Instant;
import java.time.LocalTime;

public record SysacadScheduleLogEntryDto(
        Long id,
        Instant occurredAt,
        String type,
        String result,
        String reason,
        Integer previousBuilding,
        Integer previousRoom,
        Integer newBuilding,
        Integer newRoom,
        LocalTime sentStartTime,
        Integer sentDurationMinutes,
        LocalTime readStartTime,
        Integer readDurationMinutes,
        LocalTime readEndTime,
        LocalTime rereadStartTime,
        Integer rereadDurationMinutes,
        LocalTime rereadEndTime,
        Integer attempts,
        String errorId,
        String response,
        String occurrences,
        Long occurrenceId,
        Long itemId,
        String revisionUser,
        String revisionOperation,
        String resolvedBy
) {}
