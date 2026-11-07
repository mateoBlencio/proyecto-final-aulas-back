package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import java.time.Duration;

public sealed interface UpdateRoomResult {

    record Applied(String body) implements UpdateRoomResult {}

    record ScheduleNotFound(String description, String body) implements UpdateRoomResult {}

    record RoomNotFound(String description, String body) implements UpdateRoomResult {}

    record Unparseable(String body) implements UpdateRoomResult {}

    record ServerError(int status, String errorId, String body) implements UpdateRoomResult {}

    record ConnectionFailed(String message) implements UpdateRoomResult {}

    record RateLimited(Duration retryAfter) implements UpdateRoomResult {}

    record Unauthorized(int status) implements UpdateRoomResult {}
}
