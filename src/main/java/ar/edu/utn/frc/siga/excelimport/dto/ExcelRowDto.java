package ar.edu.utn.frc.siga.excelimport.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Datos crudos ya parseados de una fila del Excel de importación (una comisión
 * dictándose en un día/horario, en un aula de un edificio), listos para resolver
 * o crear las entidades de dominio correspondientes.
 */
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
