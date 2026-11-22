package ar.edu.utn.frc.siga.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Resultado de una corrida de archivado de auditoría")
public record AuditArchiveResultDto(
        @Schema(description = "COMPLETED, NOTHING_TO_ARCHIVE (nada anterior al corte), NO_PERIODS (faltan "
                + "períodos académicos para calcular el corte) o STOPPED_BY_CONCURRENT_RUN (otra corrida tenía el lock)")
        AuditArchiveOutcome outcome,
        @Schema(description = "Primer ciclo lectivo que se conserva en línea; null si no hay períodos")
        Integer retainedFromCycle,
        @Schema(description = "Se archiva toda revisión anterior a esta fecha; null si no hay períodos")
        LocalDate cutoff,
        @Schema(description = "Filas movidas de ocurrencia_aud")
        long occurrenceRows,
        @Schema(description = "Filas movidas de asignacion_aula_aud")
        long allocationRows,
        @Schema(description = "Revisiones borradas de la tabla activa")
        long deletedRevisions) {
}
