package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadResponseTooLargeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadWriteClient {

    private static final String PROCEDURE_PATH = "/api/data/procedures/ActualizarHorarioAula";
    private static final String MESSAGE_PROCEDURE_PATH = "/api/data/procedures/EnviarMensajeComision";
    private static final String SCHEDULE_VIEW = "HorariosComisionesCupos";
    private static final ParameterizedTypeReference<ViewResponse<RawSchedule>> SCHEDULE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final SysacadProperties properties;
    private final Consumer<Duration> sleeper;

    @Autowired
    public SysacadWriteClient(@Qualifier("sysacadWriteRestClient") RestClient restClient,
                              SysacadProperties properties) {
        this(restClient, properties, SysacadClient::sleep);
    }

    SysacadWriteClient(RestClient restClient, SysacadProperties properties, Consumer<Duration> sleeper) {
        this.restClient = restClient;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public UpdateRoomResult updateRoom(UpdateRoomCommand command) {
        UpdateRoomResult result = postOnce(command);
        if (!isRetryable(result)) {
            return result;
        }
        sleeper.accept(properties.getWrite().inCallRetryDelay());
        return postOnce(command);
    }

    // Un solo intento: reintentar tras un corte puede duplicar el mensaje a toda la comisión.
    public CommissionMessageResult sendCommissionMessage(CommissionMessageCommand command) {
        try {
            String body = restClient.post()
                    .uri(MESSAGE_PROCEDURE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("params", toParams(command)))
                    .retrieve()
                    .body(String.class);
            return new CommissionMessageResult(!ProcedureResponseParser.reportsFailure(body), body);
        } catch (RestClientResponseException e) {
            return new CommissionMessageResult(false, "HTTP " + e.getStatusCode().value() + ": "
                    + e.getResponseBodyAsString());
        } catch (RestClientException | SysacadResponseTooLargeException e) {
            return new CommissionMessageResult(false, e.getMessage());
        }
    }

    public ScheduleReadResult readSchedule(int specialty, int plan, int subject) {
        ViewQuery query = new ViewQuery(Map.of(
                "Especialidad", String.valueOf(specialty),
                "Plan", String.valueOf(plan),
                "materia", String.valueOf(subject)), null, null, null);
        try {
            ViewResponse<RawSchedule> response = restClient.get()
                    .uri(uriBuilder -> SysacadClient.buildUri(uriBuilder, SCHEDULE_VIEW, query))
                    .retrieve()
                    .body(SCHEDULE_TYPE);
            List<RawSchedule> rows = response == null || response.rows() == null ? List.of() : response.rows();
            return new ScheduleReadResult.Rows(rows);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                return new ScheduleReadResult.RateLimited(SysacadClient.parseRetryAfter(e.getResponseHeaders()));
            }
            if (status == 401 || status == 403) {
                return new ScheduleReadResult.Unauthorized(status);
            }
            String body = e.getResponseBodyAsString();
            return new ScheduleReadResult.ServerError(status, ProcedureResponseParser.extractErrorId(body), body);
        } catch (ResourceAccessException e) {
            return new ScheduleReadResult.ConnectionFailed(e.getMessage());
        } catch (RestClientException e) {
            return e.getCause() instanceof HttpMessageNotReadableException
                    ? new ScheduleReadResult.ServerError(200, null, e.getMessage())
                    : new ScheduleReadResult.ConnectionFailed(e.getMessage());
        } catch (SysacadResponseTooLargeException e) {
            return new ScheduleReadResult.ServerError(200, null, e.getMessage());
        }
    }

    private UpdateRoomResult postOnce(UpdateRoomCommand command) {
        try {
            String body = restClient.post()
                    .uri(PROCEDURE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("params", toParams(command)))
                    .retrieve()
                    .body(String.class);
            return ProcedureResponseParser.parse(body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                return new UpdateRoomResult.RateLimited(SysacadClient.parseRetryAfter(e.getResponseHeaders()));
            }
            if (status == 401 || status == 403) {
                return new UpdateRoomResult.Unauthorized(status);
            }
            String body = e.getResponseBodyAsString();
            return new UpdateRoomResult.ServerError(status, ProcedureResponseParser.extractErrorId(body), body);
        } catch (RestClientException | SysacadResponseTooLargeException e) {
            // Un corte después de los headers también es "pudo aplicarse o no": hay que leer la vista antes de reintentar.
            return new UpdateRoomResult.ConnectionFailed(e.getMessage());
        }
    }

    private static boolean isRetryable(UpdateRoomResult result) {
        return result instanceof UpdateRoomResult.Unparseable
                || result instanceof UpdateRoomResult.ServerError error && error.status() >= 500;
    }

    private Map<String, Object> toParams(CommissionMessageCommand command) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("@LegajoPublica", properties.getCommissionMessagePublisherLegajo());
        params.put("@IdTipoUsuarioPublica", properties.getCommissionMessagePublisherUserType());
        params.put("@FechaHasta", command.visibleUntil().toString());
        params.put("@AnioAcademico", command.academicYear());
        params.put("@Especialidad", command.specialty());
        params.put("@Plan", command.plan());
        params.put("@Materia", command.subject());
        params.put("@Comision", command.commission());
        params.put("@Nota", command.note());
        return params;
    }

    private static Map<String, Integer> toParams(UpdateRoomCommand command) {
        Map<String, Integer> params = new LinkedHashMap<>();
        params.put("@anoacademi", command.academicYear());
        params.put("@especialid", command.specialty());
        params.put("@plan", command.plan());
        params.put("@Materia", command.subject());
        params.put("@comision", command.commission());
        params.put("@dia", command.day());
        params.put("@Cuatrimestre", command.term());
        params.put("@horacomienzo", command.startHour());
        params.put("@duracion", command.duration());
        params.put("@Edificio", command.building());
        params.put("@Aula", command.classroom());
        params.put("@recreos", 0);
        return params;
    }
}
