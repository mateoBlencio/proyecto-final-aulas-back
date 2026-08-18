package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.EventType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.modulith.NamedInterface;

import java.time.LocalTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecurringEventHistorySnapshotDto.class, name = "RECURRING"),
        @JsonSubTypes.Type(value = UniqueEventHistorySnapshotDto.class, name = "UNIQUE_EVENT")
})
@NamedInterface("api")
public sealed interface EventHistorySnapshotDto
        permits RecurringEventHistorySnapshotDto, UniqueEventHistorySnapshotDto {
    Long id();
    EventType type();
    Integer enrolled();
    LocalTime startTime();
    long durationMinutes();
}
