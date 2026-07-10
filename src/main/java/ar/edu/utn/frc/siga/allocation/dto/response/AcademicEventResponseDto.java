package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.EventType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecurringEventResponseDto.class, name = "RECURRING"),
        @JsonSubTypes.Type(value = UniqueEventResponseDto.class, name = "UNIQUE_EVENT")
})
public sealed interface AcademicEventResponseDto permits RecurringEventResponseDto, UniqueEventResponseDto {
    Long id();
    EventType type();
    Integer enrolled();
    LocalTime startTime();
    long durationMinutes();
}
