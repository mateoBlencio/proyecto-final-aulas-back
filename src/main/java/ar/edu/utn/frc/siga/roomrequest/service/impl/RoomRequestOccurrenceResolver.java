package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resuelve qué ocurrencia(s) del calendario le corresponden a un ítem al asignarlo, y libera las ocurrencias "mirror" viejas al reasignar. */
@Component
@RequiredArgsConstructor
class RoomRequestOccurrenceResolver {

    private final AllocationService allocationService;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;

    List<List<Long>> resolveOccurrencesBySlot(RoomRequestItem item, int classroomCount, boolean reassigning) {
        List<Long> principalOccurrences = resolveTargetOccurrences(item, reassigning);
        List<List<Long>> occurrencesBySlot = new ArrayList<>();
        occurrencesBySlot.add(principalOccurrences);

        if (classroomCount > 1) {
            Map<Long, List<Long>> mirrorsByPrincipal = new LinkedHashMap<>();
            for (Long principalId : principalOccurrences) {
                mirrorsByPrincipal.put(principalId, occurrenceService.createSimultaneous(principalId, classroomCount - 1));
            }
            for (int slot = 1; slot < classroomCount; slot++) {
                int mirrorIndex = slot - 1;
                occurrencesBySlot.add(principalOccurrences.stream()
                        .map(principalId -> mirrorsByPrincipal.get(principalId).get(mirrorIndex))
                        .toList());
            }
        }
        return occurrencesBySlot;
    }

    /** Los "mirror" (orden > 1) son ocurrencias efímeras que solo existen para esta resolución: se liberan y se crean de nuevo en cada assign, nunca se reutilizan entre llamadas. */
    void releaseOldMirrors(RoomRequestItem item) {
        List<Long> oldMirrorOccurrenceIds = item.getAllocations().stream()
                .filter(allocation -> allocation.getPosition() > 1)
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .distinct()
                .toList();
        if (oldMirrorOccurrenceIds.isEmpty()) {
            return;
        }
        allocationService.deallocate(new DeallocationCommand(
                List.of(new AllocationTarget.Occurrences(oldMirrorOccurrenceIds)),
                "Reasignación de pedido de aula #" + item.getId()));
        oldMirrorOccurrenceIds.forEach(occurrenceService::release);
    }

    private List<Long> resolveTargetOccurrences(RoomRequestItem item, boolean reassigning) {
        RoomRequestType type = item.getRequest().getType();
        return switch (type) {
            case ONE_TIME_ROOM_CHANGE, PARTIAL_EXAM_IN_CLASS -> List.of(findOccurrenceOnDate(item));
            case REGULAR_ROOM_CHANGE -> findFutureOccurrencesOnDayOfWeek(item);
            case PARTIAL_EXAM_OFF_SCHEDULE, FINAL_EXAM, CONFERENCE, OTHER -> reassigning
                    ? List.of(principalAllocation(item).getOccurrenceId())
                    : List.of(createEventOccurrence(item, type));
        };
    }

    private RoomRequestItemAllocation principalAllocation(RoomRequestItem item) {
        return item.getAllocations().stream()
                .filter(allocation -> allocation.getPosition() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "El pedido no tiene una asignación principal previa a reasignar: itemId=" + item.getId()));
    }

    private Long findOccurrenceOnDate(RoomRequestItem item) {
        return occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), null).stream()
                .filter(slot -> slot.date().equals(item.getDate()))
                .findFirst()
                .map(OccurrenceSlotDto::occurrenceId)
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "No se encontró la clase de este pedido en el calendario."));
    }

    private List<Long> findFutureOccurrencesOnDayOfWeek(RoomRequestItem item) {
        List<Long> occurrenceIds = occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), LocalDate.now())
                .stream()
                .filter(slot -> slot.date().getDayOfWeek() == item.getDayOfWeek())
                .map(OccurrenceSlotDto::occurrenceId)
                .toList();
        if (occurrenceIds.isEmpty()) {
            throw new InvalidRoomRequestException("No quedan clases futuras para este cambio regular de aula.");
        }
        return occurrenceIds;
    }

    private Long createEventOccurrence(RoomRequestItem item, RoomRequestType type) {
        UniqueEventKind kind = switch (type) {
            case PARTIAL_EXAM_OFF_SCHEDULE -> UniqueEventKind.PARCIAL;
            case FINAL_EXAM -> UniqueEventKind.EXAMEN_FINAL;
            default -> UniqueEventKind.OTRO;
        };
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(kind, item.getRequest().getSubjectId(),
                item.getCommissionId(), item.getDate(), item.getStartTime(),
                (int) item.getDuration().toMinutes(), item.getEstimated(), item.getObservations());
        AcademicEventResponseDto event = academicEventService.createUniqueEvent(dto);
        return academicEventService.findOccurrencesByEventId(event.id()).getFirst().id();
    }
}
