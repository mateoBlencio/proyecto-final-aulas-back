package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import java.time.LocalDate;
import java.util.List;

public record RunReport(Result result, List<ReviewedSchedule> reviewed, List<ClassWithoutRoom> classesWithoutRoom,
        List<String> errors) {

    public enum Result {
        SKIPPED_PAUSED, SKIPPED_HOURS_CHANGED, OK, VIEW_READ_FAILED, STOPPED_CONNECTION, STOPPED_RATE_LIMITED,
        STOPPED_UNAUTHORIZED, STOPPED_HOURS_CHANGED, STOPPED_FAILURES, ERROR
    }

    public record ReviewedSchedule(Long scheduleId, SysacadReviewReason reason) {
    }

    public record ClassWithoutRoom(Long scheduleId, Long occurrenceId, LocalDate date) {
    }
}
