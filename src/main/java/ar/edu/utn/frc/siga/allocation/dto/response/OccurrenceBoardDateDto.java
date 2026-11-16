package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OccurrenceBoardDateDto(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean past,
        int requiredRooms,
        List<OccurrenceBoardSlotDto> slots,
        List<OccurrenceIssueDto> issues
) {
}
