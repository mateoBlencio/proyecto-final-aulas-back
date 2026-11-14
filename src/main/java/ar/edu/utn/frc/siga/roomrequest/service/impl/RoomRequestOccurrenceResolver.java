package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.roomrequest.direct.DirectEventKinds;
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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** Libera los espejos futuros del pedido y devuelve sus ids; los pasados quedan como historial. */
    List<Long> releaseOldMirrors(RoomRequestItem item) {
        List<Long> oldMirrorOccurrenceIds = onlyFuture(item, item.getAllocations().stream()
                .filter(allocation -> allocation.getPosition() > 1)
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .distinct()
                .toList());
        if (oldMirrorOccurrenceIds.isEmpty()) {
            return List.of();
        }
        allocationService.deallocate(new DeallocationCommand(
                List.of(new AllocationTarget.Occurrences(oldMirrorOccurrenceIds)),
                "Reasignación de pedido de aula #" + item.getId()));
        occurrenceService.release(
                oldMirrorOccurrenceIds.stream().map(id -> new OccurrenceRelease(id, null)).toList(),
                ReleaseReason.MIRROR_RETIRED, "Reasignación de pedido de aula #" + item.getId());
        return oldMirrorOccurrenceIds;
    }

    /** En un cambio regular las clases pasadas conservan su aula: solo las futuras se pueden liberar. */
    List<Long> onlyFuture(RoomRequestItem item, List<Long> occurrenceIds) {
        if (item.getRequest().getType() != RoomRequestType.REGULAR_ROOM_CHANGE || occurrenceIds.isEmpty()) {
            return occurrenceIds;
        }
        Set<Long> futureIds = occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), LocalDate.now())
                .stream()
                .filter(slot -> !slot.isPast())
                .map(OccurrenceSlotDto::occurrenceId)
                .collect(Collectors.toSet());
        return occurrenceIds.stream().filter(futureIds::contains).toList();
    }

    /** Fecha de la próxima clase de un cambio regular, o vacío si no quedan. */
    Optional<LocalDate> nextClassDate(RoomRequestItem item) {
        return slotsOnDayOfWeekFromToday(item).stream().map(OccurrenceSlotDto::date).min(Comparator.naturalOrder());
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
                .filter(slot -> slot.mirrorOfOccurrenceId() == null)
                .filter(slot -> slot.date().equals(item.getDate()))
                .findFirst()
                .map(OccurrenceSlotDto::occurrenceId)
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "No se encontró la clase de este pedido en el calendario."));
    }

    List<LocalDate> futureDatesOnDayOfWeek(RoomRequestItem item) {
        return futureSlotsOnDayOfWeek(item).stream().map(OccurrenceSlotDto::date).toList();
    }

    private List<Long> findFutureOccurrencesOnDayOfWeek(RoomRequestItem item) {
        return futureSlotsOnDayOfWeek(item).stream().map(OccurrenceSlotDto::occurrenceId).toList();
    }

    List<OccurrenceSlotDto> futureSlotsOnDayOfWeek(RoomRequestItem item) {
        List<OccurrenceSlotDto> slots = slotsOnDayOfWeekFromToday(item);
        if (slots.isEmpty()) {
            throw new InvalidRoomRequestException("No quedan clases futuras para este cambio regular de aula.");
        }
        return slots;
    }

    private List<OccurrenceSlotDto> slotsOnDayOfWeekFromToday(RoomRequestItem item) {
        return occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), LocalDate.now())
                .stream()
                .filter(slot -> slot.mirrorOfOccurrenceId() == null)
                .filter(slot -> slot.date().getDayOfWeek() == item.getDayOfWeek())
                .filter(slot -> !slot.isPast())
                .toList();
    }

    private Long createEventOccurrence(RoomRequestItem item, RoomRequestType type) {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(DirectEventKinds.of(type),
                item.getRequest().getSubjectId(), item.getCommissionId(), item.getDate(), item.getStartTime(),
                (int) item.getDuration().toMinutes(), item.getEstimated(), item.getObservations(),
                item.getRequiresProjector(), item.getRequiresComputers(), item.getComputerCount(),
                item.getRequiredSoftware(), item.getRequiresExamUsers());
        AcademicEventResponseDto event = academicEventService.createUniqueEvent(dto);
        return academicEventService.findOccurrencesByEventId(event.id()).getFirst().id();
    }
}
