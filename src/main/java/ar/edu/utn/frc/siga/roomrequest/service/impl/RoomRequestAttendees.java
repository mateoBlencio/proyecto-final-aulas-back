package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Asistentes de un pedido: lo estimado por el docente o, si no, los inscriptos del evento de cursado. */
@Component
@RequiredArgsConstructor
class RoomRequestAttendees {

    private final AcademicEventService academicEventService;

    Optional<Integer> of(RoomRequestItem item) {
        if (item.getEstimated() != null) {
            return Optional.of(item.getEstimated());
        }
        Long sourceEventId = item.getSourceRecurringEventId();
        if (sourceEventId == null) {
            return Optional.empty();
        }
        return academicEventService.findByIds(Set.of(sourceEventId)).stream()
                .map(AcademicEventResponseDto::enrolled)
                .filter(Objects::nonNull)
                .findFirst();
    }
}
