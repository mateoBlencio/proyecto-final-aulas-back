package ar.edu.utn.frc.siga.allocation.events.dto.response;

import ar.edu.utn.frc.siga.allocation.events.model.EventType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalTime;

/**
 * Snapshot histórico de un {@code AcademicEvent} en una revisión de auditoría, polimórfico
 * por {@code type} ({@code RECURRING}/{@code UNIQUE_EVENT}) como {@link AcademicEventResponseDto}.
 * IDs planos sin resolver contra el catálogo actual: el historial muestra el dato crudo de
 * ese momento.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecurringEventHistorySnapshotDto.class, name = "RECURRING"),
        @JsonSubTypes.Type(value = UniqueEventHistorySnapshotDto.class, name = "UNIQUE_EVENT")
})
public sealed interface EventHistorySnapshotDto
        permits RecurringEventHistorySnapshotDto, UniqueEventHistorySnapshotDto {
    Long id();
    EventType type();
    Integer enrolled();
    LocalTime startTime();
    long durationMinutes();
}
