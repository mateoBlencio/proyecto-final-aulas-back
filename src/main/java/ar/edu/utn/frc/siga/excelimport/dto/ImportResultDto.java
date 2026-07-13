package ar.edu.utn.frc.siga.excelimport.dto;

/**
 * Resumen de una importación de Excel: cuántas filas se procesaron y cuántas
 * asignaciones/entidades de dominio se crearon versus se reutilizaron (ya existían).
 */
public record ImportResultDto(
    int processedRows,
    int assignmentsCreated,
    int assignmentsReused,
    int entitiesCreated,
    int entitiesReused
) {}
