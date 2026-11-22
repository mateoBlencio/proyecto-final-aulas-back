package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;

public record OccurrenceBoardSlotDto(
        Long occurrenceId,
        Integer roomSlot,
        OccurrenceStatus status,
        ReleaseReason releaseReason,
        String releaseNote,
        OccurrenceBoardAllocationDto allocation,
        OccurrenceBoardClassroomDto previousClassroom
) {
}
