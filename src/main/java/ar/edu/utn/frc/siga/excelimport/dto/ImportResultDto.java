package ar.edu.utn.frc.siga.excelimport.dto;

import java.util.List;

public record ImportResultDto(
    int processedRows,
    int periodsCreated,
    int eventsCreated,
    List<RowIssueDto> skippedRows,
    List<RowIssueDto> rowWarnings
) {}
