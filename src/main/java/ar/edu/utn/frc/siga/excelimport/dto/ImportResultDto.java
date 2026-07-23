package ar.edu.utn.frc.siga.excelimport.dto;

import java.util.List;

/**
 * Resumen de una importación de Excel: filas procesadas, períodos académicos creados
 * (único dato que este flujo crea), filas salteadas por no resolver contra el catálogo
 * (p. ej. aula inexistente) y filas importadas igual pero con alguna inconsistencia
 * tolerada (p. ej. edificio informado no coincide con el real del aula).
 */
public record ImportResultDto(
    int processedRows,
    int periodsCreated,
    List<RowIssueDto> skippedRows,
    List<RowIssueDto> rowWarnings
) {}
