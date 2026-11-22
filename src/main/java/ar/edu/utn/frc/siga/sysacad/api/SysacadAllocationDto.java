package ar.edu.utn.frc.siga.sysacad.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record SysacadAllocationDto(
        String courseCode,
        Integer subjectCode,
        Integer specialtyCode,
        Integer studyPlanCode,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Integer durationMinutes,
        LocalTime endTime,
        Integer commissionNumber,
        Integer semester,
        Integer roomNumber,
        Integer buildingCode
) {}
