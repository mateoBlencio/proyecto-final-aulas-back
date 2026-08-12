package ar.edu.utn.frc.siga.preview.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record MoveConflictDto(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer classroomId,
        Long conflictingEventId,
        ConflictOrigin origin) {

    public enum ConflictOrigin {
        DATABASE, PREVIEW
    }
}
