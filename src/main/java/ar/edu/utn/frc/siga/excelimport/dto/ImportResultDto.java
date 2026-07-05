package ar.edu.utn.frc.siga.excelimport.dto;

public record ImportResultDto(
    int processedRows,
    int assignmentsCreated,
    int assignmentsReused,
    int entitiesCreated,
    int entitiesReused
) {}
