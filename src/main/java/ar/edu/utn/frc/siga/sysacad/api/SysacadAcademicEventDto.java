package ar.edu.utn.frc.siga.sysacad.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record SysacadAcademicEventDto(
        String courseCode,
        Integer subjectCode,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Integer durationMinutes,
        Integer semester
) {}
