package ar.edu.utn.frc.siga.sysacad.api;

import java.time.Instant;

public record SysacadScheduleDto(
        Long id,
        String courseCode,
        Integer subject,
        Integer commission,
        Integer dayOfWeek,
        Integer semester,
        String status,
        Integer knownBuilding,
        Integer knownRoom,
        Integer baseBuilding,
        Integer baseRoom,
        Integer readBuilding,
        Integer readRoom,
        String reviewReason,
        Instant reviewSince
) {}
