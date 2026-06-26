package ar.edu.utn.frc.classroom_allocation.excelimport.dto;

public record ImportResultDto(
    int processedRows,
    int assignmentsCreated,
    int assignmentsReused,
    int entitiesCreated,
    int entitiesReused
) {}
