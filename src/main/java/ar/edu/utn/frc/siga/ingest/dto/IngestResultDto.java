package ar.edu.utn.frc.siga.ingest.dto;

import java.util.List;

public record IngestResultDto(
    int processedRows,
    int periodsCreated,
    int eventsCreated,
    List<RowIssueDto> skippedRows,
    List<RowIssueDto> rowWarnings
) {}
