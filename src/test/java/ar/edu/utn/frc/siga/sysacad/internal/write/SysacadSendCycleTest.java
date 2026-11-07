package ar.edu.utn.frc.siga.sysacad.internal.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationLastRevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.sync.ReconcileOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadSendCycle")
class SysacadSendCycleTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final long SCHEDULE_ID = 1L;
    private static final long OCCURRENCE_ID = 100L;
    private static final int YEAR = 2026;
    private static final int SPECIALTY = 5;
    private static final int PLAN = 2008;
    private static final int SUBJECT = 115;
    private static final int COMMISSION = 7;
    private static final int DAY = 1;
    private static final int SEMESTER = 1;
    private static final int BUILDING = 5;
    private static final int KNOWN_ROOM = 228;
    private static final int NEW_ROOM = 229;
    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 3, 16);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(10, 15);
    private static final ScheduleTimes TIMES = new ScheduleTimes(START, 135, END);

    @Mock
    private SysacadWriteClient client;
    @Mock
    private SysacadScheduleStateWriter stateWriter;
    @Mock
    private DirectChangeHandler directChangeHandler;
    @Mock
    private AllocationService allocationService;

    private SysacadProperties properties;
    private SysacadSendCycle cycle;
    private RequestPacer pacer;
    private SysacadSchedule schedule;

    @BeforeEach
    void setUp() {
        properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.ON));
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * *", 50));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        cycle = new SysacadSendCycle(client, new SysacadCatalogMapper(), stateWriter, directChangeHandler,
                allocationService, properties, clock);
        pacer = new RequestPacer(clock, wait -> { }, 30);
        schedule = SysacadSchedule.builder().id(SCHEDULE_ID)
                .year(YEAR).specialty(SPECIALTY).plan(PLAN).subject(SUBJECT).commission(COMMISSION)
                .dayOfWeek(DAY).semester(SEMESTER).knownBuilding(BUILDING).knownRoom(KNOWN_ROOM)
                .build();
    }

    private static RawSchedule raw(int room, String start, String end, Integer duration) {
        return new RawSchedule("1K1", COMMISSION, room, BUILDING, "Edificio", DAY, SEMESTER, "A", "A", start, end,
                start + "-" + end, duration, SPECIALTY, "Sistemas", PLAN, SUBJECT, "Materia", 0);
    }

    private static RawSchedule rawSiga(int room) {
        return raw(room, "08:00", "10:15", 135);
    }

    private static RawSchedule unreadable() {
        return raw(NEW_ROOM, "no-es-hora", "10:15", 135);
    }

    private static ScheduleReadResult rows(RawSchedule... rows) {
        return new ScheduleReadResult.Rows(List.of(rows));
    }

    private static Send decision() {
        return new Send(BUILDING, NEW_ROOM, OCCURRENCE_ID, CLASS_DATE, START, END);
    }

    private void readsReturn(ScheduleReadResult first, ScheduleReadResult... next) {
        when(client.readSchedule(SPECIALTY, PLAN, SUBJECT)).thenReturn(first, next);
    }

    private void postReturns(UpdateRoomResult result) {
        when(client.updateRoom(any())).thenReturn(result);
    }

    private SendOutcome send() {
        return cycle.send(schedule, decision(), pacer);
    }

    @Test
    @DisplayName("Envío confirmado: el comando lleva la hora y duración leídas de la vista, y markIntent va antes del POST")
    void confirmedSendUsesViewHoursAndRecordsIntentFirst() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));
        when(allocationService.findLastRevision(OCCURRENCE_ID))
                .thenReturn(Optional.of(new AllocationLastRevisionDto("subsecretaria@frc.utn.edu.ar", "op-1")));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.CONFIRMED);
        InOrder order = inOrder(stateWriter, client);
        order.verify(stateWriter).markIntent(SCHEDULE_ID, BUILDING, NEW_ROOM, OCCURRENCE_ID, null, TIMES, START, 135);
        order.verify(client).updateRoom(any());
        order.verify(stateWriter).confirm(SCHEDULE_ID, OCCURRENCE_ID, null, "subsecretaria@frc.utn.edu.ar", "op-1",
                TIMES);
        ArgumentCaptor<UpdateRoomCommand> command = ArgumentCaptor.forClass(UpdateRoomCommand.class);
        verify(client).updateRoom(command.capture());
        assertThat(command.getValue()).isEqualTo(new UpdateRoomCommand(YEAR, SPECIALTY, PLAN, SUBJECT, COMMISSION,
                DAY, SEMESTER, 800, 135, BUILDING, NEW_ROOM));
    }

    @Test
    @DisplayName("Envío confirmado sin revisión de auditoría: confirma con usuario y operación nulos")
    void confirmedWithoutRevisionKeepsNullActor() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));
        when(allocationService.findLastRevision(OCCURRENCE_ID)).thenReturn(Optional.empty());

        send();

        verify(stateWriter).confirm(SCHEDULE_ID, OCCURRENCE_ID, null, null, null, TIMES);
    }

    @Test
    @DisplayName("Una hora anterior a las 10 viaja sin cero a la izquierda: 09:40 es 940")
    void hourBeforeTenTravelsAsNineForty() {
        readsReturn(rows(raw(KNOWN_ROOM, "09:40", "11:55", 135)), rows(raw(NEW_ROOM, "09:40", "11:55", 135)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = cycle.send(schedule,
                new Send(BUILDING, NEW_ROOM, OCCURRENCE_ID, CLASS_DATE, LocalTime.of(9, 40), LocalTime.of(11, 55)),
                pacer);

        assertThat(outcome.kind()).isEqualTo(Kind.CONFIRMED);
        ArgumentCaptor<UpdateRoomCommand> command = ArgumentCaptor.forClass(UpdateRoomCommand.class);
        verify(client).updateRoom(command.capture());
        assertThat(command.getValue().startHour()).isEqualTo(940);
    }

    @Test
    @DisplayName("Timeout que sí se aplicó: la relectura muestra el aula nueva y confirma sin reenviar")
    void timeoutThatWasAppliedConfirms() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.ConnectionFailed("timeout"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.CONFIRMED);
        verify(client, times(1)).updateRoom(any());
        verify(stateWriter).confirm(eq(SCHEDULE_ID), eq(OCCURRENCE_ID), isNull(), any(), any(), eq(TIMES));
    }

    @Test
    @DisplayName("Timeout que no se aplicó: registra el fallo sin sumar intento, corta por conexión y no reintenta el POST")
    void timeoutThatWasNotAppliedStopsWithoutCountingAttempt() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.ConnectionFailed("timeout"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_CONNECTION);
        verify(client, times(1)).updateRoom(any());
        verify(stateWriter).recordFailure(SCHEDULE_ID, "CONNECTION_FAILED", false, null, "CONNECTION_FAILED", null,
                null);
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
        verify(stateWriter, never()).toReview(any(), any(), anyCollection());
    }

    @Test
    @DisplayName("Timeout y la relectura también falla: corta por conexión sin confirmar ni reenviar")
    void timeoutAndRereadFailureStopsByConnection() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), new ScheduleReadResult.ConnectionFailed("sin red"));
        postReturns(new UpdateRoomResult.ConnectionFailed("timeout"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_CONNECTION);
        verify(client, times(1)).updateRoom(any());
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest(name = "relectura con hora {0}, duración {1} y fin {2}")
    @CsvSource({
            "09:00, 135, 10:15",
            "08:00, 90, 10:15",
            "08:00, 135, 11:00"
    })
    @DisplayName("Relectura con hora, duración o HoraFin distinta: reenvía los valores previos completos y deja el horario en revisión")
    void changedHoursAfterSendRevertsAndOpensReview(String start, int duration, String end) {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(raw(NEW_ROOM, start, end, duration)));
        when(client.updateRoom(any())).thenReturn(new UpdateRoomResult.Applied("ok"),
                new UpdateRoomResult.Applied("revert"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_HOURS_CHANGED);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.HOURS_CHANGED_AFTER_SEND);
        ArgumentCaptor<UpdateRoomCommand> commands = ArgumentCaptor.forClass(UpdateRoomCommand.class);
        verify(client, times(2)).updateRoom(commands.capture());
        assertThat(commands.getAllValues().get(1)).isEqualTo(new UpdateRoomCommand(YEAR, SPECIALTY, PLAN, SUBJECT,
                COMMISSION, DAY, SEMESTER, 800, 135, BUILDING, KNOWN_ROOM));
        verify(stateWriter).toReviewAfterRevert(SCHEDULE_ID, List.of(OCCURRENCE_ID),
                new ScheduleTimes(LocalTime.parse(start), duration, LocalTime.parse(end)), "APPLIED");
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Timeout y relectura con hora distinta: también reenvía los valores previos y abre revisión")
    void timeoutThenChangedHoursReverts() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(raw(NEW_ROOM, "09:00", "10:15", 135)));
        when(client.updateRoom(any())).thenReturn(new UpdateRoomResult.ConnectionFailed("timeout"),
                new UpdateRoomResult.Applied("revert"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_HOURS_CHANGED);
        verify(client, times(2)).updateRoom(any());
        verify(stateWriter).toReviewAfterRevert(SCHEDULE_ID, List.of(OCCURRENCE_ID),
                new ScheduleTimes(LocalTime.of(9, 0), 135, END), "APPLIED");
    }

    @Test
    @DisplayName("Si el reenvío de los valores previos falla igual deja el horario en revisión")
    void failedRevertStillOpensReview() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(raw(NEW_ROOM, "09:00", "10:15", 135)));
        when(client.updateRoom(any())).thenReturn(new UpdateRoomResult.Applied("ok"),
                new UpdateRoomResult.ServerError(500, "E-9", "falló"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_HOURS_CHANGED);
        verify(stateWriter).toReviewAfterRevert(SCHEDULE_ID, List.of(OCCURRENCE_ID),
                new ScheduleTimes(LocalTime.of(9, 0), 135, END), "SERVER_ERROR");
    }

    @Test
    @DisplayName("Reversión: guarda la intención antes del POST de reversión y la revisión lleva las horas releídas")
    void revertRecordsIntentBeforePostAndReviewWithRereadHours() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(raw(NEW_ROOM, "09:00", "10:15", 135)));
        when(client.updateRoom(any())).thenReturn(new UpdateRoomResult.Applied("ok"),
                new UpdateRoomResult.Applied("revert"));

        send();

        InOrder order = inOrder(stateWriter, client);
        order.verify(stateWriter).markIntent(SCHEDULE_ID, BUILDING, NEW_ROOM, OCCURRENCE_ID, null, TIMES, START, 135);
        order.verify(client).updateRoom(any());
        order.verify(stateWriter).markRevertIntent(SCHEDULE_ID, BUILDING, KNOWN_ROOM, OCCURRENCE_ID, TIMES, START,
                135);
        order.verify(client).updateRoom(any());
        order.verify(stateWriter).toReviewAfterRevert(SCHEDULE_ID, List.of(OCCURRENCE_ID),
                new ScheduleTimes(LocalTime.of(9, 0), 135, END), "APPLIED");
    }

    @Test
    @DisplayName("Reversión cuyo reenvío no se pudo conectar: igual abre la revisión con el resultado CONNECTION_FAILED")
    void revertWithConnectionFailureRecordsResult() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(raw(NEW_ROOM, "09:00", "10:15", 135)));
        when(client.updateRoom(any())).thenReturn(new UpdateRoomResult.Applied("ok"),
                new UpdateRoomResult.ConnectionFailed("sin red"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_HOURS_CHANGED);
        verify(stateWriter).toReviewAfterRevert(eq(SCHEDULE_ID), eq(List.of(OCCURRENCE_ID)), any(),
                eq("CONNECTION_FAILED"));
    }

    @Test
    @DisplayName("Relectura con 429: corta con el Retry-After")
    void rereadRateLimitedStopsWithWait() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), new ScheduleReadResult.RateLimited(Duration.ofSeconds(90)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_RATE_LIMITED);
        assertThat(outcome.retryAfter()).isEqualTo(Duration.ofSeconds(90));
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Relectura con 401: corta por credenciales")
    void rereadUnauthorizedStops() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), new ScheduleReadResult.Unauthorized(401));
        postReturns(new UpdateRoomResult.Applied("ok"));

        assertThat(send().kind()).isEqualTo(Kind.STOP_UNAUTHORIZED);
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Relectura con falla de conexión o error de servidor: corta por conexión")
    void rereadConnectionOrServerErrorStopsByConnection() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), new ScheduleReadResult.ServerError(500, "E-1", "boom"));
        postReturns(new UpdateRoomResult.Applied("ok"));

        assertThat(send().kind()).isEqualTo(Kind.STOP_CONNECTION);
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Relectura con filas duplicadas que no quedaron iguales: no confirma")
    void rereadWithNonMatchingDuplicatesDoesNotConfirm() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM), rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        assertThat(outcome.failure().result()).isEqualTo("NOT_CONFIRMED");
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Relectura con una fila ilegible junto a una que muestra el aula nueva: no confirma")
    void rereadWithUnreadableRowDoesNotConfirm() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM), unreadable()));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        verify(stateWriter, never()).confirm(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Relectura con filas duplicadas idénticas que muestran el aula nueva: confirma")
    void rereadWithIdenticalDuplicatesConfirms() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM), rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        assertThat(send().kind()).isEqualTo(Kind.CONFIRMED);
    }

    @Test
    @DisplayName("Applied sin aula nueva la primera vez: SCHEDULE_FAILED con NOT_CONFIRMED")
    void appliedWithoutNewRoomFirstTimeFails() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        assertThat(outcome.failure().result()).isEqualTo("NOT_CONFIRMED");
        assertThat(outcome.failure().error()).isEqualTo("NOT_CONFIRMED");
        verify(stateWriter, never()).toReview(any(), any(), anyCollection());
    }

    @Test
    @DisplayName("Applied sin aula nueva con lastError NOT_CONFIRMED: pasa a revisión")
    void appliedWithoutNewRoomTwiceOpensReview() {
        schedule.setLastError("NOT_CONFIRMED");
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.REVIEW);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.NOT_CONFIRMED);
        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.NOT_CONFIRMED, List.of(OCCURRENCE_ID));
    }

    @Test
    @DisplayName("Lectura previa distinta del valor conocido: no hay POST y DIRECT_CHANGE invoca al handler")
    void priorReadDifferentFromKnownReconcilesAndInvokesHandler() {
        readsReturn(rows(rawSiga(230)));
        when(stateWriter.reconcile(SCHEDULE_ID, BUILDING, 230)).thenReturn(ReconcileOutcome.DIRECT_CHANGE);

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SKIPPED);
        verify(directChangeHandler).handle(SCHEDULE_ID);
        verify(client, never()).updateRoom(any());
        verify(stateWriter, never()).markIntent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lectura previa distinta del valor conocido sin cambio directo: reconcilia pero no llama al handler")
    void priorReadDifferentWithoutDirectChangeSkipsHandler() {
        readsReturn(rows(rawSiga(230)));
        when(stateWriter.reconcile(SCHEDULE_ID, BUILDING, 230)).thenReturn(ReconcileOutcome.DIFFERENCE_RECORDED);

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SKIPPED);
        verifyNoInteractions(directChangeHandler);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Lectura previa con otro edificio y el mismo número de aula: también reconcilia")
    void priorReadInOtherBuildingReconciles() {
        RawSchedule otherBuilding = new RawSchedule("1K1", COMMISSION, KNOWN_ROOM, 6, "Otro", DAY, SEMESTER, "A", "A",
                "08:00", "10:15", "08:00-10:15", 135, SPECIALTY, "Sistemas", PLAN, SUBJECT, "Materia", 0);
        readsReturn(rows(otherBuilding));

        send();

        verify(stateWriter).reconcile(SCHEDULE_ID, 6, KNOWN_ROOM);
        verify(client, never()).updateRoom(any());
    }

    @ParameterizedTest(name = "vista {0}-{1} dur {2}")
    @CsvSource({
            "08:30, 10:45, 135",
            "08:00, 10:00, 120"
    })
    @DisplayName("Control previo: hora o duración de la vista distinta de la de SIGA pasa a revisión HOUR_MISMATCH sin POST")
    void hourMismatchOpensReviewWithoutPost(String start, String end, int duration) {
        readsReturn(rows(raw(KNOWN_ROOM, start, end, duration)));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.REVIEW);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.HOUR_MISMATCH);
        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.HOUR_MISMATCH, List.of(OCCURRENCE_ID));
        verify(client, never()).updateRoom(any());
        verify(stateWriter, never()).markIntent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Control previo: si la vista no trae duración se trata como HOUR_MISMATCH y no se envía")
    void missingViewDurationIsHourMismatch() {
        readsReturn(rows(raw(KNOWN_ROOM, "08:00", "10:15", null)));

        SendOutcome outcome = send();

        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.HOUR_MISMATCH);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Control previo: si SIGA no tiene hora de fin se trata como HOUR_MISMATCH y no se envía")
    void missingSigaEndTimeIsHourMismatch() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));

        SendOutcome outcome = cycle.send(schedule,
                new Send(BUILDING, NEW_ROOM, OCCURRENCE_ID, CLASS_DATE, START, null), pacer);

        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.HOUR_MISMATCH);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Filas con aulas distintas antes de enviar: ROWS_DIVERGED y no hay POST")
    void divergentRowsBeforeSendOpenReview() {
        readsReturn(rows(rawSiga(KNOWN_ROOM), rawSiga(230)));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.REVIEW);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.ROWS_DIVERGED);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Filas con horas distintas antes de enviar: ROWS_DIVERGED y no hay POST")
    void rowsWithDifferentHoursBeforeSendOpenReview() {
        readsReturn(rows(rawSiga(KNOWN_ROOM), raw(KNOWN_ROOM, "09:00", "11:15", 135)));

        SendOutcome outcome = send();

        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.ROWS_DIVERGED);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Una fila ilegible entre las de la clave antes de enviar: ROWS_DIVERGED y no hay POST")
    void unreadableRowBeforeSendOpensReview() {
        readsReturn(rows(rawSiga(KNOWN_ROOM), unreadable()));

        SendOutcome outcome = send();

        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.ROWS_DIVERGED);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Filas idénticas antes de enviar: se envía un solo POST")
    void identicalRowsBeforeSendProduceOnePost() {
        readsReturn(rows(rawSiga(KNOWN_ROOM), rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.CONFIRMED);
        verify(client, times(1)).updateRoom(any());
    }

    @Test
    @DisplayName("Las filas de otra comisión, día o cuatrimestre no cuentan para la clave del horario")
    void rowsOfOtherKeysAreIgnored() {
        RawSchedule otherCommission = new RawSchedule("1K1", 8, 250, BUILDING, "Edificio", DAY, SEMESTER, "A", "A",
                "08:00", "10:15", "08:00-10:15", 135, SPECIALTY, "Sistemas", PLAN, SUBJECT, "Materia", 0);
        RawSchedule otherDay = new RawSchedule("1K1", COMMISSION, 251, BUILDING, "Edificio", 2, SEMESTER, "A", "A",
                "08:00", "10:15", "08:00-10:15", 135, SPECIALTY, "Sistemas", PLAN, SUBJECT, "Materia", 0);
        readsReturn(rows(otherCommission, rawSiga(KNOWN_ROOM), otherDay), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        assertThat(send().kind()).isEqualTo(Kind.CONFIRMED);
    }

    @Test
    @DisplayName("Sin filas en la vista, primera vez: reintenta después del próximo sync sin sumar intento")
    void noRowsFirstTimeRetriesAfterNextSync() {
        readsReturn(rows());

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SKIPPED);
        // Sync a las 03:00 de Buenos Aires del 11/03 son las 06:00Z, más la hora de margen.
        Instant expected = Instant.parse("2026-03-11T07:00:00Z");
        verify(stateWriter).recordFailure(eq(SCHEDULE_ID), eq("SCHEDULE_NOT_FOUND"), eq(false), eq(expected),
                eq("SCHEDULE_NOT_FOUND"), anyString(), isNull());
        assertThat(expected).isAfter(NOW);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Sin filas y sin cron configurado: reintenta en un día")
    void noRowsWithoutCronRetriesInOneDay() {
        properties.setSync(new SysacadProperties.Sync(null, 50));
        readsReturn(rows());

        send();

        verify(stateWriter).recordFailure(eq(SCHEDULE_ID), eq("SCHEDULE_NOT_FOUND"), eq(false),
                eq(NOW.plus(Duration.ofDays(1))), anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("Sin filas y el horario ya venía con SCHEDULE_NOT_FOUND: pasa a revisión")
    void noRowsSecondTimeOpensReview() {
        schedule.setLastError("SCHEDULE_NOT_FOUND");
        readsReturn(rows());

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.REVIEW);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.SCHEDULE_NOT_FOUND);
        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.SCHEDULE_NOT_FOUND, List.of());
        verify(stateWriter, never()).recordFailure(any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ScheduleNotFound del POST la primera vez: reintenta después del próximo sync sin sumar intento")
    void scheduleNotFoundOnPostFirstTime() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.ScheduleNotFound("no existe", "{cuerpo}"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SKIPPED);
        verify(stateWriter).recordFailure(SCHEDULE_ID, "SCHEDULE_NOT_FOUND", false,
                Instant.parse("2026-03-11T07:00:00Z"), "SCHEDULE_NOT_FOUND", "{cuerpo}", null);
    }

    @Test
    @DisplayName("ScheduleNotFound del POST con SCHEDULE_NOT_FOUND previo: pasa a revisión")
    void scheduleNotFoundOnPostSecondTime() {
        schedule.setLastError("SCHEDULE_NOT_FOUND");
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.ScheduleNotFound("no existe", "{cuerpo}"));

        SendOutcome outcome = send();

        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.SCHEDULE_NOT_FOUND);
        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.SCHEDULE_NOT_FOUND, List.of());
    }

    @Test
    @DisplayName("RoomNotFound: revisión ROOM_NOT_IN_SYSACAD sin reintentar")
    void roomNotFoundOpensReviewWithoutRetry() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.RoomNotFound("aula inexistente", "{cuerpo}"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.REVIEW);
        assertThat(outcome.reviewReason()).isEqualTo(SysacadReviewReason.ROOM_NOT_IN_SYSACAD);
        verify(client, times(1)).updateRoom(any());
        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.ROOM_NOT_IN_SYSACAD, List.of(OCCURRENCE_ID));
    }

    @Test
    @DisplayName("Unparseable: SCHEDULE_FAILED con el cuerpo de la respuesta")
    void unparseableIsScheduleFailureWithBody() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.Unparseable("<html>raro</html>"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        assertThat(outcome.failure().result()).isEqualTo("UNPARSEABLE");
        assertThat(outcome.failure().response()).isEqualTo("<html>raro</html>");
    }

    @Test
    @DisplayName("ServerError: SCHEDULE_FAILED con el Error id y el cuerpo")
    void serverErrorKeepsErrorIdAndBody() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.ServerError(500, "ERR-77", "{\"error\":\"x\"}"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        assertThat(outcome.failure()).isEqualTo(new SendOutcome.Failure("SERVER_ERROR",
                "HTTP 500 en ActualizarHorarioAula", "{\"error\":\"x\"}", "ERR-77"));
    }

    @Test
    @DisplayName("RateLimited: corta con el Retry-After y no suma intento")
    void rateLimitedStopsWithoutCountingAttempt() {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.RateLimited(Duration.ofSeconds(90)));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_RATE_LIMITED);
        assertThat(outcome.retryAfter()).isEqualTo(Duration.ofSeconds(90));
        verify(stateWriter).recordFailure(SCHEDULE_ID, "RATE_LIMITED", false, null, "RATE_LIMITED", null, null);
    }

    @ParameterizedTest
    @CsvSource({"401", "403"})
    @DisplayName("Unauthorized: corta por credenciales y no suma intento")
    void unauthorizedStopsWithoutCountingAttempt(int status) {
        readsReturn(rows(rawSiga(KNOWN_ROOM)));
        postReturns(new UpdateRoomResult.Unauthorized(status));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_UNAUTHORIZED);
        verify(stateWriter).recordFailure(SCHEDULE_ID, "UNAUTHORIZED_" + status, false, null,
                "UNAUTHORIZED_" + status, null, null);
    }

    @Test
    @DisplayName("Falla de conexión al leer la vista antes de enviar: corta sin registrar intención ni hacer POST")
    void readConnectionFailureStopsBeforeIntent() {
        readsReturn(new ScheduleReadResult.ConnectionFailed("sin red"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_CONNECTION);
        verify(client, never()).updateRoom(any());
        verifyNoInteractions(stateWriter);
    }

    @Test
    @DisplayName("429 al leer la vista antes de enviar: corta con el Retry-After")
    void readRateLimitedStops() {
        readsReturn(new ScheduleReadResult.RateLimited(Duration.ofSeconds(30)));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.STOP_RATE_LIMITED);
        assertThat(outcome.retryAfter()).isEqualTo(Duration.ofSeconds(30));
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("401 al leer la vista antes de enviar: corta por credenciales")
    void readUnauthorizedStops() {
        readsReturn(new ScheduleReadResult.Unauthorized(401));

        assertThat(send().kind()).isEqualTo(Kind.STOP_UNAUTHORIZED);
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Error de servidor al leer la vista antes de enviar: SCHEDULE_FAILED con Error id y cuerpo")
    void readServerErrorIsScheduleFailure() {
        readsReturn(new ScheduleReadResult.ServerError(502, "ERR-1", "bad gateway"));

        SendOutcome outcome = send();

        assertThat(outcome.kind()).isEqualTo(Kind.SCHEDULE_FAILED);
        assertThat(outcome.failure().result()).isEqualTo("READ_SERVER_ERROR");
        assertThat(outcome.failure().errorId()).isEqualTo("ERR-1");
        assertThat(outcome.failure().response()).isEqualTo("bad gateway");
        verify(client, never()).updateRoom(any());
    }

    @Test
    @DisplayName("Cada pedido a SysAcad pasa por el pacer: lectura, POST y relectura")
    void everyRequestGoesThroughPacer() {
        RequestPacer countingPacer = org.mockito.Mockito.mock(RequestPacer.class);
        readsReturn(rows(rawSiga(KNOWN_ROOM)), rows(rawSiga(NEW_ROOM)));
        postReturns(new UpdateRoomResult.Applied("ok"));

        cycle.send(schedule, decision(), countingPacer);

        verify(countingPacer, times(3)).acquire();
    }
}
