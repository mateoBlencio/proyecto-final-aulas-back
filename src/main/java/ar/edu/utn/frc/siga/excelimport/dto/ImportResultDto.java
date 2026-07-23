package ar.edu.utn.frc.siga.excelimport.dto;

/**
 * Resumen de una importación de Excel: filas procesadas y períodos académicos
 * creados (único dato que este flujo crea; el resto del catálogo se busca y falla
 * si no existe).
 */
public record ImportResultDto(
    int processedRows,
    int periodsCreated
) {}
