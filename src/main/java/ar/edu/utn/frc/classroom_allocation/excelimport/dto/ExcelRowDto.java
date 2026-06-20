package ar.edu.utn.frc.classroom_allocation.excelimport.dto;

public record ExcelRowDto(
    String courseCode,
    Integer commissionNumber,
    String roomNumber,
    String buildingName,
    String dayOfWeek,
    String termType,
    Integer startTime,
    Integer endTime,
    Integer durationMinutes,
    Integer specialtyCode,
    Integer studyPlanCode,
    Integer subjectCode,
    String subjectName,
    Integer enrolledCount
) {}
