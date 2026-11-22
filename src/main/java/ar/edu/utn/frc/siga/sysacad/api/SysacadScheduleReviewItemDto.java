package ar.edu.utn.frc.siga.sysacad.api;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code type}: REVIEW, NOT_SENDABLE o WAITING. {@code since} es null en los WAITING. La clase e ítem que
 * frenan ({@code blocking*}) vienen solo en los WAITING.
 */
public record SysacadScheduleReviewItemDto(
        Long id,
        String courseCode,
        Integer subject,
        Integer commission,
        Integer dayOfWeek,
        Integer semester,
        String type,
        String reason,
        Instant since,
        Integer knownBuilding,
        Integer knownRoom,
        Integer readBuilding,
        Integer readRoom,
        ConfirmedSend lastConfirmedSend,
        Long blockingOccurrenceId,
        LocalDate blockingDate,
        Long blockingItemId
) {

    public record ConfirmedSend(Instant occurredAt, Integer building, Integer room, String result) {}
}
