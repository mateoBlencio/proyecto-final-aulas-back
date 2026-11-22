package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcedureResponseParser: doble parseo de ActualizarHorarioAula")
class ProcedureResponseParserTest {

    private static final String APPLIED = "{\"apiName\":\"ActualizarHorarioAula\",\"columns\":null,\"limitApplied\":null,"
            + "\"objectType\":\"PROCEDURE\",\"outputParams\":{},\"resultSets\":[{\"columns\":[\"JSON\"],\"rows\":"
            + "[{\"JSON\":\"\\r\\n\\t\\t{\\\"Estado\\\":\\\"1\\\",\\\"Descripcion\\\":\\\"Cambio Realizado !\\\"}\"}]}],\"rows\":null}";

    private static final String SCHEDULE_NOT_FOUND_DESCRIPTION = "No se actualizo porque este horario no existe! "
            + "Facultad:4!2026!9!2023!101!1!5!0!1455!135!0!Edificio: 2!Aula: 228";
    private static final String ROOM_NOT_FOUND_DESCRIPTION = "No se actualizo porque el Aula-Edificio Indicado no existe ! "
            + "Facultad:4!2026!9!2023!101!1!2!0!1455!135!0!Edificio: 2!Aula: 98765";

    private static String envelope(String state, String description) {
        return "{\"apiName\":\"ActualizarHorarioAula\",\"resultSets\":[{\"columns\":[\"JSON\"],\"rows\":"
                + "[{\"JSON\":\"\\r\\n\\t\\t{\\\"Estado\\\":\\\"" + state + "\\\",\\\"Descripcion\\\":\\\""
                + description + "\\\"}\"}]}],\"rows\":null}";
    }

    @Test
    @DisplayName("Estado 1 con el JSON dentro de un string y espacios adelante: Applied con el cuerpo crudo")
    void stateOneIsApplied() {
        assertThat(ProcedureResponseParser.parse(APPLIED)).isEqualTo(new UpdateRoomResult.Applied(APPLIED));
    }

    @Test
    @DisplayName("Estado 0 con 'este horario no existe': ScheduleNotFound con la descripción")
    void scheduleNotFound() {
        String body = envelope("0", SCHEDULE_NOT_FOUND_DESCRIPTION);

        assertThat(ProcedureResponseParser.parse(body))
                .isEqualTo(new UpdateRoomResult.ScheduleNotFound(SCHEDULE_NOT_FOUND_DESCRIPTION, body));
    }

    @Test
    @DisplayName("Estado 0 con 'el Aula-Edificio Indicado no existe': RoomNotFound con la descripción")
    void roomNotFound() {
        String body = envelope("0", ROOM_NOT_FOUND_DESCRIPTION);

        assertThat(ProcedureResponseParser.parse(body))
                .isEqualTo(new UpdateRoomResult.RoomNotFound(ROOM_NOT_FOUND_DESCRIPTION, body));
    }

    @Test
    @DisplayName("Estado 0 con texto desconocido: Unparseable")
    void unknownDescriptionIsUnparseable() {
        String body = envelope("0", "Error inesperado");

        assertThat(ProcedureResponseParser.parse(body)).isEqualTo(new UpdateRoomResult.Unparseable(body));
    }

    @Test
    @DisplayName("Estado desconocido, aunque la descripción coincida con 'horario no existe': Unparseable")
    void unknownStateIsUnparseable() {
        String body = envelope("2", SCHEDULE_NOT_FOUND_DESCRIPTION);

        assertThat(ProcedureResponseParser.parse(body)).isEqualTo(new UpdateRoomResult.Unparseable(body));
    }

    @Test
    @DisplayName("JSON externo roto: Unparseable sin lanzar")
    void brokenOuterJson() {
        String body = "{\"resultSets\":[{";

        assertThat(ProcedureResponseParser.parse(body)).isEqualTo(new UpdateRoomResult.Unparseable(body));
    }

    @Test
    @DisplayName("JSON interno roto: Unparseable sin lanzar")
    void brokenInnerJson() {
        String body = "{\"resultSets\":[{\"columns\":[\"JSON\"],\"rows\":[{\"JSON\":\"{Estado:\"}]}]}";

        assertThat(ProcedureResponseParser.parse(body)).isEqualTo(new UpdateRoomResult.Unparseable(body));
    }

    @Test
    @DisplayName("resultSets vacío: Unparseable")
    void emptyResultSets() {
        String body = "{\"apiName\":\"ActualizarHorarioAula\",\"resultSets\":[],\"rows\":null}";

        assertThat(ProcedureResponseParser.parse(body)).isEqualTo(new UpdateRoomResult.Unparseable(body));
    }

    @Test
    @DisplayName("cuerpo nulo: Unparseable sin lanzar")
    void nullBody() {
        assertThat(ProcedureResponseParser.parse(null)).isEqualTo(new UpdateRoomResult.Unparseable(null));
    }

    @Test
    @DisplayName("cuerpo vacío: Unparseable sin lanzar")
    void emptyBody() {
        assertThat(ProcedureResponseParser.parse("")).isEqualTo(new UpdateRoomResult.Unparseable(""));
    }

    @Test
    @DisplayName("extractErrorId devuelve el uuid con su sufijo")
    void extractsErrorId() {
        String body = "500 - Internal Server Error. Error id e33f7028-a3ce-4b22-a30f-a453c700217e-1";

        assertThat(ProcedureResponseParser.extractErrorId(body)).isEqualTo("e33f7028-a3ce-4b22-a30f-a453c700217e-1");
    }

    @Test
    @DisplayName("extractErrorId devuelve null sin id o con cuerpo nulo")
    void errorIdAbsent() {
        assertThat(ProcedureResponseParser.extractErrorId("500 - Internal Server Error")).isNull();
        assertThat(ProcedureResponseParser.extractErrorId(null)).isNull();
    }

    @Test
    @DisplayName("reportsFailure: Estado 0, cuerpo vacío o no JSON son fallo; un JSON sin Estado no lo es")
    void reportsFailure() {
        assertThat(ProcedureResponseParser.reportsFailure(envelope("0", "No se envio"))).isTrue();
        assertThat(ProcedureResponseParser.reportsFailure(envelope("1", "Enviado"))).isFalse();
        assertThat(ProcedureResponseParser.reportsFailure("{}")).isFalse();
        assertThat(ProcedureResponseParser.reportsFailure("esto no es json")).isTrue();
        assertThat(ProcedureResponseParser.reportsFailure("<html>Bad gateway</html>")).isTrue();
        assertThat(ProcedureResponseParser.reportsFailure("")).isTrue();
        assertThat(ProcedureResponseParser.reportsFailure("   ")).isTrue();
        assertThat(ProcedureResponseParser.reportsFailure(null)).isTrue();
    }
}
