package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSysacadRefDto;
import ar.edu.utn.frc.siga.sysacad.internal.model.DestinationNotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoNextClass;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoRoom;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoRoomAfterCancel;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NotSendable;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Unchanged;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Waiting;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class NextClassCalculator {

    static final int RELEASED_ROOM = 999;

    private static final Comparator<OccurrenceSlotDto> BY_START =
            Comparator.comparing(OccurrenceSlotDto::date).thenComparing(OccurrenceSlotDto::startTime);

    private NextClassCalculator() {
    }

    /** Próxima clase principal del horario: sin espejos ni feriados, y que todavía no llegó a su hora de fin. */
    public static Optional<OccurrenceSlotDto> findNext(SysacadSchedule schedule, Collection<OccurrenceSlotDto> slots,
            Set<LocalDate> holidays, LocalDateTime now) {
        return slots.stream()
                .filter(slot -> slot.mirrorOfOccurrenceId() == null)
                .filter(slot -> !holidays.contains(slot.date()))
                .filter(slot -> slot.date().atTime(endOf(schedule, slot)).isAfter(now))
                .min(BY_START);
    }

    public static NextClassDecision calculate(SysacadSchedule schedule, Collection<OccurrenceSlotDto> slots,
            Map<Long, Long> classroomByOccurrence, Map<Long, ClassroomSysacadRefDto> refsByClassroom,
            Map<Long, OccurrenceRequestStateDto> requestStates, Set<LocalDate> holidays, LocalDateTime now) {
        Optional<OccurrenceSlotDto> next = findNext(schedule, slots, holidays, now);
        if (next.isEmpty()) {
            return new NoNextClass();
        }
        OccurrenceSlotDto slot = next.get();
        Long occurrenceId = slot.occurrenceId();
        OccurrenceRequestStateDto request = requestStates.get(occurrenceId);
        OccurrenceRequestState requestState = request == null ? null : request.state();

        if (requestState == OccurrenceRequestState.PENDING) {
            return new Waiting(occurrenceId, request.itemId(), slot.date(), slot.startTime());
        }
        if (slot.status() == OccurrenceStatus.ROOM_RELEASED) {
            if (schedule.getKnownBuilding() == null) {
                return new NoRoom(occurrenceId, slot.date());
            }
            return Objects.equals(schedule.getKnownRoom(), RELEASED_ROOM)
                    ? new Unchanged(occurrenceId, slot.date())
                    : sendUnlessStarted(slot, now, schedule.getKnownBuilding(), RELEASED_ROOM);
        }
        Long classroomId = classroomByOccurrence.get(occurrenceId);
        if (classroomId == null) {
            return requestState == OccurrenceRequestState.CANCELLED
                    ? new NoRoomAfterCancel(occurrenceId, request.itemId(), slot.date())
                    : new NoRoom(occurrenceId, slot.date());
        }
        ClassroomSysacadRefDto ref = refsByClassroom.get(classroomId);
        DestinationNotSendableReason invalid = invalidReason(ref);
        if (invalid != null) {
            return new NotSendable(occurrenceId, slot.date(), invalid);
        }
        return Objects.equals(ref.buildingCode(), schedule.getKnownBuilding())
                && Objects.equals(ref.roomNumber(), schedule.getKnownRoom())
                ? new Unchanged(occurrenceId, slot.date())
                : sendUnlessStarted(slot, now, ref.buildingCode(), ref.roomNumber());
    }

    // La clase empezada sigue siendo la próxima hasta su HoraFin y SIGA no puede cambiarla: sin esto un cambio
    // directo en SysAcad se revertiría hasta que termine la clase.
    private static NextClassDecision sendUnlessStarted(OccurrenceSlotDto slot, LocalDateTime now, Integer building,
            Integer room) {
        if (!slot.date().atTime(slot.startTime()).isAfter(now)) {
            return new Unchanged(slot.occurrenceId(), slot.date());
        }
        return new Send(building, room, slot.occurrenceId(), slot.date(), slot.startTime(), slot.endTime());
    }

    private static DestinationNotSendableReason invalidReason(ClassroomSysacadRefDto ref) {
        if (ref == null) {
            return DestinationNotSendableReason.CLASSROOM_NOT_FOUND;
        }
        if (!ref.buildingCurrentInSysacad()) {
            return DestinationNotSendableReason.BUILDING_NOT_IN_SYSACAD;
        }
        if (!ref.currentInSysacad()) {
            return DestinationNotSendableReason.CLASSROOM_NOT_IN_SYSACAD;
        }
        if (!ref.enabledInSysacad()) {
            return DestinationNotSendableReason.CLASSROOM_DISABLED_IN_SYSACAD;
        }
        if (ref.roomNumber() == null) {
            return DestinationNotSendableReason.CLASSROOM_WITHOUT_NUMBER;
        }
        // SysAcad lee 999 como "sin aula" y 0 no tiene efecto: un aula de SIGA con esos números no se puede mandar.
        if (ref.roomNumber() <= 0 || ref.roomNumber() == RELEASED_ROOM) {
            return DestinationNotSendableReason.CLASSROOM_RESERVED_NUMBER;
        }
        if (ref.buildingCode() == null) {
            return DestinationNotSendableReason.BUILDING_WITHOUT_CODE;
        }
        return null;
    }

    private static LocalTime endOf(SysacadSchedule schedule, OccurrenceSlotDto slot) {
        return schedule.getEndTime() != null ? schedule.getEndTime() : slot.endTime();
    }
}
