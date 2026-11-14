package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import ar.edu.utn.frc.siga.sysacad.internal.model.DestinationNotSendableReason;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public sealed interface NextClassDecision {

    record Unchanged(Long occurrenceId, LocalDate date) implements NextClassDecision {
    }

    record Send(Integer building, Integer room, Long occurrenceId, LocalDate date, LocalTime startTime,
            LocalTime endTime, List<RoomRef> rooms) implements NextClassDecision {
    }

    record NotifyOnly(Long occurrenceId, LocalDate date, List<RoomRef> rooms) implements NextClassDecision {
    }

    record Waiting(Long occurrenceId, Long itemId, LocalDate date, LocalTime startTime)
            implements NextClassDecision {
    }

    record NoNextClass() implements NextClassDecision {
    }

    record NoRoom(Long occurrenceId, LocalDate date) implements NextClassDecision {
    }

    record NoRoomAfterCancel(Long occurrenceId, Long itemId, LocalDate date) implements NextClassDecision {
    }

    record NotSendable(Long occurrenceId, LocalDate date, DestinationNotSendableReason reason)
            implements NextClassDecision {
    }
}
