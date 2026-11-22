package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import java.util.List;

public record AllocationSearchResultDto(Long eventId, EventType eventType, UniqueEventKind uniqueEventType,
        String subject, String section, String specialtyName, List<AllocationSearchOccurrenceDto> occurrences) {
}
