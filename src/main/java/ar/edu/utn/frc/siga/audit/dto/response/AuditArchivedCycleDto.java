package ar.edu.utn.frc.siga.audit.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AuditArchivedCycleDto(
        int cycle,
        LocalDate from,
        LocalDate to,
        LocalDateTime archivedAt,
        long occurrenceRows,
        long allocationRows) {
}
