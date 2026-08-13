package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record UniqueEventHistorySnapshotDto(
        Long id,
        EventType type,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        LocalDate date,
        String description,
        UniqueEventKind eventType,
        Long subjectId,
        Long commissionId
) implements EventHistorySnapshotDto {
}
