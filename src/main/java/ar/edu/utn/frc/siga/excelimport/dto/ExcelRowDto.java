package ar.edu.utn.frc.siga.excelimport.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ExcelRowDto(
    String courseCode,
    Integer commissionNumber,
    String roomNumber,
    String buildingName,
    DayOfWeek dayOfWeek,
    String termType,
    LocalTime startTime,
    LocalTime endTime,
    Integer durationMinutes,
    Integer specialtyCode,
    Integer studyPlanCode,
    Integer subjectCode,
    String subjectName,
    Integer enrolledCount
) {}
