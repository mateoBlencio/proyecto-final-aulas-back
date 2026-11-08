package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadResponseTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("SysacadWriteClient: ActualizarHorarioAula y lectura de horarios")
class SysacadWriteClientTest {

    private static final String PROCEDURE_URL = "http://sysacad.test/api/data/procedures/ActualizarHorarioAula";
    private static final String MESSAGE_URL = "http://sysacad.test/api/data/procedures/EnviarMensajeComision";
    private static final CommissionMessageCommand MESSAGE = new CommissionMessageCommand(
            LocalDate.of(2026, 11, 17), 2026, 9, 2023, 101, 1, "Cambio de aula");
    private static final String VIEW_PATH = "/api/data/views/HorariosComisionesCupos";
    private static final String ERROR_ID = "e33f7028-a3ce-4b22-a30f-a453c700217e-1";
    private static final String ERROR_BODY = "500 - Internal Server Error. Error id " + ERROR_ID;

    private static final String EXPECTED_BODY = "{\"params\":{\"@anoacademi\":2026,\"@especialid\":9,\"@plan\":2023,"
            + "\"@Materia\":101,\"@comision\":1,\"@dia\":2,\"@Cuatrimestre\":0,\"@horacomienzo\":1455,"
            + "\"@duracion\":135,\"@Edificio\":2,\"@Aula\":229,\"@recreos\":0}}";

    private static final String APPLIED = envelope("1", "Cambio Realizado !");
    private static final String SCHEDULE_NOT_FOUND = envelope("0",
            "No se actualizo porque este horario no existe! Facultad:4!2026!9!2023!101!1!5!0!1455!135!0!Edificio: 2!Aula: 228");
    private static final String ROOM_NOT_FOUND = envelope("0",
            "No se actualizo porque el Aula-Edificio Indicado no existe ! Facultad:4!2026!9!2023!101!1!2!0!1455!135!0!Edificio: 2!Aula: 98765");

    private static final UpdateRoomCommand COMMAND = new UpdateRoomCommand(2026, 9, 2023, 101, 1, 2, 0, 1455, 135, 2, 229);
    private static final Duration IN_CALL_DELAY = Duration.ofSeconds(2);

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private SysacadWriteClient client;

    private static String envelope(String state, String description) {
        return "{\"apiName\":\"ActualizarHorarioAula\",\"resultSets\":[{\"columns\":[\"JSON\"],\"rows\":"
                + "[{\"JSON\":\"\\r\\n\\t\\t{\\\"Estado\\\":\\\"" + state + "\\\",\\\"Descripcion\\\":\\\""
                + description + "\\\"}\"}]}],\"rows\":null}";
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://sysacad.test");
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps.clear();
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.ON));
        client = new SysacadWriteClient(builder.build(), properties, sleeps::add);
    }

    @Test
    @DisplayName("el POST va al procedure con JSON y el body exacto de los 12 parámetros con @recreos en 0")
    void sendsExactRequest() {
        server.expect(requestTo(PROCEDURE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(EXPECTED_BODY))
                .andRespond(withSuccess(APPLIED, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isInstanceOf(UpdateRoomResult.Applied.class);

        server.verify();
    }

    @Test
    @DisplayName("200 con Estado 1: Applied sin reintento")
    void appliedWithoutRetry() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess(APPLIED, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Applied(APPLIED));

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("200 con Estado 0 'este horario no existe': ScheduleNotFound sin reintento")
    void scheduleNotFoundWithoutRetry() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess(SCHEDULE_NOT_FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isInstanceOf(UpdateRoomResult.ScheduleNotFound.class);

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("200 con Estado 0 'Aula-Edificio Indicado no existe': RoomNotFound sin reintento")
    void roomNotFoundWithoutRetry() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess(ROOM_NOT_FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isInstanceOf(UpdateRoomResult.RoomNotFound.class);

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("200 sin parsear y segundo intento sin parsear: un reintento y Unparseable")
    void unparseableRetriesOnceThenGivesUp() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess("<html>caído</html>", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Unparseable("{}"));

        assertThat(sleeps).containsExactly(IN_CALL_DELAY);
        server.verify();
    }

    @Test
    @DisplayName("200 sin parsear y segundo intento correcto: Applied tras esperar inCallRetryDelay")
    void unparseableThenApplied() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess(APPLIED, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Applied(APPLIED));

        assertThat(sleeps).containsExactly(IN_CALL_DELAY);
        server.verify();
    }

    @Test
    @DisplayName("500 en texto plano dos veces: un reintento tras inCallRetryDelay y ServerError con Error id y cuerpo")
    void serverErrorRetriesOnce() {
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(PROCEDURE_URL))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.TEXT_PLAIN).body(ERROR_BODY));
        }

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.ServerError(500, ERROR_ID, ERROR_BODY));

        assertThat(sleeps).containsExactly(IN_CALL_DELAY);
        server.verify();
    }

    @Test
    @DisplayName("500 y después 200 correcto: Applied")
    void serverErrorThenApplied() {
        server.expect(requestTo(PROCEDURE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(ERROR_BODY));
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withSuccess(APPLIED, MediaType.APPLICATION_JSON));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Applied(APPLIED));

        assertThat(sleeps).containsExactly(IN_CALL_DELAY);
    }

    @Test
    @DisplayName("500 sin Error id: ServerError con errorId nulo")
    void serverErrorWithoutId() {
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(PROCEDURE_URL))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("500 - Internal Server Error"));
        }

        assertThat(client.updateRoom(COMMAND))
                .isEqualTo(new UpdateRoomResult.ServerError(500, null, "500 - Internal Server Error"));
    }

    @Test
    @DisplayName("400 no reintenta y devuelve ServerError")
    void badRequestIsNotRetried() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("mal armado"));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.ServerError(400, null, "mal armado"));

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    private static InputStream failingBody(RuntimeException failure) {
        return new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        };
    }

    private static InputStream brokenBody() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("conexión cortada");
            }
        };
    }

    @Test
    @DisplayName("updateRoom: un corte a mitad del cuerpo da ConnectionFailed sin reintento, porque pudo aplicarse")
    void bodyCutAfterHeadersIsConnectionFailed() {
        server.expect(requestTo(PROCEDURE_URL))
                .andRespond(request -> new MockClientHttpResponse(brokenBody(), HttpStatus.OK));

        UpdateRoomResult result = client.updateRoom(COMMAND);

        assertThat(result).isInstanceOf(UpdateRoomResult.ConnectionFailed.class);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("updateRoom: una respuesta demasiado grande da ConnectionFailed sin lanzar")
    void tooLargeResponseIsConnectionFailed() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(request -> new MockClientHttpResponse(
                failingBody(new SysacadResponseTooLargeException(10)), HttpStatus.OK));

        assertThat(client.updateRoom(COMMAND)).isInstanceOf(UpdateRoomResult.ConnectionFailed.class);
        server.verify();
    }

    @Test
    @DisplayName("readSchedule: un corte a mitad del cuerpo da ConnectionFailed, igual que antes de los headers")
    void readBodyCutIsConnectionFailed() {
        server.expect(method(HttpMethod.GET))
                .andRespond(request -> new MockClientHttpResponse(brokenBody(), HttpStatus.OK));

        assertThat(client.readSchedule(9, 2023, 101)).isInstanceOf(ScheduleReadResult.ConnectionFailed.class);
        server.verify();
    }

    @Test
    @DisplayName("readSchedule: una respuesta demasiado grande da ServerError sin lanzar")
    void readTooLargeResponseIsServerError() {
        server.expect(method(HttpMethod.GET)).andRespond(request -> new MockClientHttpResponse(
                failingBody(new SysacadResponseTooLargeException(10)), HttpStatus.OK));

        assertThat(client.readSchedule(9, 2023, 101)).isInstanceOf(ScheduleReadResult.ServerError.class);
        server.verify();
    }

    @Test
    @DisplayName("timeout: ConnectionFailed sin reintento")
    void timeoutIsNotRetried() {
        server.expect(requestTo(PROCEDURE_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.updateRoom(COMMAND)).isInstanceOf(UpdateRoomResult.ConnectionFailed.class);

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("conexión caída: ConnectionFailed sin reintento")
    void connectionDropIsNotRetried() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withException(new IOException("Connection reset")));

        UpdateRoomResult result = client.updateRoom(COMMAND);

        assertThat(result).isInstanceOf(UpdateRoomResult.ConnectionFailed.class);
        assertThat(((UpdateRoomResult.ConnectionFailed) result).message()).contains("Connection reset");
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("429 con Retry-After: RateLimited con esa espera y sin reintento")
    void rateLimitedWithRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.RateLimited(Duration.ofSeconds(7)));

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("429 sin Retry-After: RateLimited con retryAfter nulo")
    void rateLimitedWithoutRetryAfter() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.RateLimited(null));

        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("401: Unauthorized sin reintento")
    void unauthorizedIsNotRetried() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Invalid API Key"));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Unauthorized(401));

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("403: Unauthorized sin reintento")
    void forbiddenIsNotRetried() {
        server.expect(requestTo(PROCEDURE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.updateRoom(COMMAND)).isEqualTo(new UpdateRoomResult.Unauthorized(403));

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("readSchedule manda los tres filtros por query param y devuelve las filas")
    void readScheduleReturnsRows() {
        String body = "{\"rows\":[{\"Curso\":\"1R1\",\"Comision\":1,\"Aula\":228,\"Edificio\":2,"
                + "\"HoraComienzo\":\"14:55\",\"DURACION\":135}]}";
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo(VIEW_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("Especialidad", "9"))
                .andExpect(queryParam("Plan", "2023"))
                .andExpect(queryParam("materia", "101"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ScheduleReadResult result = client.readSchedule(9, 2023, 101);

        assertThat(result).isInstanceOf(ScheduleReadResult.Rows.class);
        List<RawSchedule> rows = ((ScheduleReadResult.Rows) result).rows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).curso()).isEqualTo("1R1");
        assertThat(rows.get(0).aula()).isEqualTo(228);
        assertThat(rows.get(0).horaComienzo()).isEqualTo("14:55");
        assertThat(rows.get(0).duracion()).isEqualTo(135);
        server.verify();
    }

    @Test
    @DisplayName("readSchedule con rows nulo o ausente devuelve Rows vacío")
    void readScheduleNullRows() {
        server.expect(request -> { }).andRespond(withSuccess("{\"rows\":null}", MediaType.APPLICATION_JSON));
        server.expect(request -> { }).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.readSchedule(9, 2023, 101)).isEqualTo(new ScheduleReadResult.Rows(List.of()));
        assertThat(client.readSchedule(9, 2023, 101)).isEqualTo(new ScheduleReadResult.Rows(List.of()));
    }

    @Test
    @DisplayName("readSchedule: timeout es ConnectionFailed y no reintenta")
    void readScheduleConnectionFailed() {
        server.expect(request -> { }).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(client.readSchedule(9, 2023, 101)).isInstanceOf(ScheduleReadResult.ConnectionFailed.class);

        server.verify();
    }

    @Test
    @DisplayName("readSchedule: 429 con y sin Retry-After es RateLimited y no reintenta")
    void readScheduleRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "12");
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.readSchedule(9, 2023, 101))
                .isEqualTo(new ScheduleReadResult.RateLimited(Duration.ofSeconds(12)));
        assertThat(client.readSchedule(9, 2023, 101)).isEqualTo(new ScheduleReadResult.RateLimited(null));

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("readSchedule: 401 y 403 son Unauthorized")
    void readScheduleUnauthorized() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.readSchedule(9, 2023, 101)).isEqualTo(new ScheduleReadResult.Unauthorized(401));
        assertThat(client.readSchedule(9, 2023, 101)).isEqualTo(new ScheduleReadResult.Unauthorized(403));
    }

    @Test
    @DisplayName("readSchedule: 500 es ServerError con Error id y no reintenta")
    void readScheduleServerError() {
        server.expect(request -> { })
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(ERROR_BODY));

        assertThat(client.readSchedule(9, 2023, 101))
                .isEqualTo(new ScheduleReadResult.ServerError(500, ERROR_ID, ERROR_BODY));

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("readSchedule: cuerpo ilegible es ServerError con status 200 y no lanza")
    void readScheduleUnreadableBody() {
        server.expect(request -> { }).andRespond(withSuccess("esto no es json", MediaType.APPLICATION_JSON));

        ScheduleReadResult result = client.readSchedule(9, 2023, 101);

        assertThat(result).isInstanceOf(ScheduleReadResult.ServerError.class);
        assertThat(((ScheduleReadResult.ServerError) result).status()).isEqualTo(200);
        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: el POST va a EnviarMensajeComision con los 9 parámetros")
    void sendsExactCommissionMessage() {
        server.expect(requestTo(MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string("{\"params\":{\"@LegajoPublica\":0,\"@IdTipoUsuarioPublica\":\"S\","
                        + "\"@FechaHasta\":\"2026-11-17\",\"@AnioAcademico\":2026,\"@Especialidad\":9,"
                        + "\"@Plan\":2023,\"@Materia\":101,\"@Comision\":1,\"@Nota\":\"Cambio de aula\"}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.sendCommissionMessage(MESSAGE)).isEqualTo(new CommissionMessageResult(true, "{}"));

        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: Estado 0 en una respuesta 200 cuenta como no entregado")
    void commissionMessageRejectedInBody() {
        String body = envelope("0", "No se envio");
        server.expect(requestTo(MESSAGE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.sendCommissionMessage(MESSAGE)).isEqualTo(new CommissionMessageResult(false, body));

        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: un 200 con HTML de un proxy cuenta como no entregado")
    void commissionMessageHtmlBodyIsNotDelivered() {
        String body = "<html>Bad gateway</html>";
        server.expect(requestTo(MESSAGE_URL)).andRespond(withSuccess(body, MediaType.TEXT_HTML));

        assertThat(client.sendCommissionMessage(MESSAGE)).isEqualTo(new CommissionMessageResult(false, body));

        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: un 200 sin cuerpo cuenta como no entregado")
    void commissionMessageEmptyBodyIsNotDelivered() {
        server.expect(requestTo(MESSAGE_URL)).andRespond(withSuccess());

        assertThat(client.sendCommissionMessage(MESSAGE).delivered()).isFalse();

        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: un 500 no se reintenta, para no duplicar el mensaje")
    void commissionMessageServerErrorIsNotRetried() {
        server.expect(requestTo(MESSAGE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(ERROR_BODY));

        CommissionMessageResult result = client.sendCommissionMessage(MESSAGE);

        assertThat(result.delivered()).isFalse();
        assertThat(result.response()).startsWith("HTTP 500");
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("sendCommissionMessage: un corte de conexión no lanza ni se reintenta")
    void commissionMessageConnectionFailureIsNotRetried() {
        server.expect(requestTo(MESSAGE_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(client.sendCommissionMessage(MESSAGE).delivered()).isFalse();

        assertThat(sleeps).isEmpty();
        server.verify();
    }
}
