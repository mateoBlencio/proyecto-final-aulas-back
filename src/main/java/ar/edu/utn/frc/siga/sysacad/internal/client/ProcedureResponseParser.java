package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProcedureResponseParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern ERROR_ID = Pattern.compile("Error id\\s+([0-9A-Za-z-]+)");
    private static final String SCHEDULE_NOT_FOUND = "este horario no existe";
    private static final String ROOM_NOT_FOUND = "aula-edificio indicado no existe";

    private ProcedureResponseParser() {}

    public static UpdateRoomResult parse(String body) {
        try {
            JsonNode inner = innerJson(body);
            String state = inner.path("Estado").asString("");
            String description = inner.path("Descripcion").asString("");
            String normalized = description.toLowerCase(Locale.ROOT);
            if ("1".equals(state)) {
                return new UpdateRoomResult.Applied(body);
            }
            if ("0".equals(state)) {
                if (normalized.contains(SCHEDULE_NOT_FOUND)) {
                    return new UpdateRoomResult.ScheduleNotFound(description, body);
                }
                if (normalized.contains(ROOM_NOT_FOUND)) {
                    return new UpdateRoomResult.RoomNotFound(description, body);
                }
            }
            return new UpdateRoomResult.Unparseable(body);
        } catch (RuntimeException e) {
            return new UpdateRoomResult.Unparseable(body);
        }
    }

    public static boolean reportsFailure(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            return "0".equals(innerJson(body).path("Estado").asString(""));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static JsonNode innerJson(String body) {
        JsonNode outer = MAPPER.readTree(body);
        return MAPPER.readTree(outer.path("resultSets").path(0).path("rows").path(0).path("JSON").asString(""));
    }

    public static String extractErrorId(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = ERROR_ID.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
