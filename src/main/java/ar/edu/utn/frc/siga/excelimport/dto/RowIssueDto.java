package ar.edu.utn.frc.siga.excelimport.dto;

/** Fila de Excel con un problema de datos: saltada (error) o importada con reserva (warning). */
public record RowIssueDto(int row, String message) {}
