package ar.edu.utn.frc.siga.events.dto;

import ar.edu.utn.frc.siga.events.model.EventType;

public record AcademicEventFilter(
        Long subjectId,
        Long commissionId,
        EventType type
) {
}
