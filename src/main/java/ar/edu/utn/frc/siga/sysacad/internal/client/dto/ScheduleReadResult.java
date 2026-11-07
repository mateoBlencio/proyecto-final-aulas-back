package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import java.time.Duration;
import java.util.List;

public sealed interface ScheduleReadResult {

    record Rows(List<RawSchedule> rows) implements ScheduleReadResult {}

    record ConnectionFailed(String message) implements ScheduleReadResult {}

    record RateLimited(Duration retryAfter) implements ScheduleReadResult {}

    record Unauthorized(int status) implements ScheduleReadResult {}

    record ServerError(int status, String errorId, String body) implements ScheduleReadResult {}
}
