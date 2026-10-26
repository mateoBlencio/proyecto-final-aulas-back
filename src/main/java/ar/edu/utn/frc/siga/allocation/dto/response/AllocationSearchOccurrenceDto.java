package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record AllocationSearchOccurrenceDto(Long allocationId, Long occurrenceId, LocalDate date,
        LocalTime startTime, LocalTime endTime, Long classroomId, String classroomName, Long buildingId) {
}
