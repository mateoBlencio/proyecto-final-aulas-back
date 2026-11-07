package ar.edu.utn.frc.siga.sysacad.internal.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.DestinationNotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.DryRunChange;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.NoRoomAfterCancel;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.NotSendable;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.Waiting;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport.Result;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Failure;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Kind;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadViewReader.ViewReadResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadWriteRun")
class SysacadWriteRunTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 3, 16);
    private static final Failure FAILURE = new Failure("SERVER_ERROR", "HTTP 500", "cuerpo", "ERR-1");

    @Mock
    private SysacadWriteStateService stateService;
    @Mock
    private SysacadViewReader viewReader;
    @Mock
    private SysacadWritePlanner planner;
    @Mock
    private SysacadSendCycle sendCycle;
    @Mock
    private SysacadScheduleStateWriter scheduleWriter;
    @Mock
    private DirectChangeHandler directChangeHandler;
    @Mock
    private SysacadScheduleRepository scheduleRepository;

    private SysacadProperties properties;
    private SysacadWriteState state;
    private SysacadWriteRun run;

    @BeforeEach
    void setUp() {
        properties = new SysacadProperties();
        configure(SysacadWriteMode.ON, 5, 3, 30, List.of(Duration.ofMinutes(5), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(1)));
        state = new SysacadWriteState();
        state.setLastViewReadAt(NOW.minus(Duration.ofMinutes(10)));
        run = new SysacadWriteRun(stateService, viewReader, planner, sendCycle, scheduleWriter, directChangeHandler,
                scheduleRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(stateService.current()).thenAnswer(invocation -> state);
    }

    private void configure(SysacadWriteMode mode, int maxAttempts, int maxFailures, int maxSends,
            List<Duration> backoff) {
        SysacadProperties.Write base = SysacadProperties.Write.of(mode);
        properties.setWrite(new SysacadProperties.Write(mode, base.jobDelay(), base.viewReadInterval(), maxAttempts,
                maxFailures, backoff, base.readTimeout(), base.inCallRetryDelay(), base.outageAlertAfter(),
                base.outageAlertRepeat(), maxSends, base.maxRequestsPerMinute(), base.recentSendWindow(),
                base.waitingVisibleBefore()));
    }

    private static SysacadSchedule schedule(long id) {
        return SysacadSchedule.builder().id(id).year(2026).specialty(5).plan(2008).subject(100 + (int) id)
                .commission(7).dayOfWeek(1).semester(1).knownBuilding(5).knownRoom(228).build();
    }

    private static PlannedSchedule send(SysacadSchedule schedule) {
        return new PlannedSchedule(schedule, sendDecision(schedule.getId()));
    }

    private static Send sendDecision(long id) {
        return new Send(5, 229, 1000 + id, CLASS_DATE, LocalTime.of(8, 0), LocalTime.of(10, 15));
    }

    private void planReturns(PlannedSchedule... plan) {
        when(planner.plan(any())).thenReturn(List.of(plan));
    }

    private void sendReturns(SysacadSchedule schedule, SendOutcome outcome) {
        when(sendCycle.send(eq(schedule), any(), any())).thenReturn(outcome);
    }

    private static SendOutcome outcome(Kind kind) {
        return new SendOutcome(kind, null, null, null);
    }

    private static SendOutcome failed() {
        return new SendOutcome(Kind.SCHEDULE_FAILED, null, null, FAILURE);
    }

    @Test
    @DisplayName("DRY_RUN nunca llama al ciclo de envío: solo deja la fila DRY_RUN_SEND de cada horario")
    void dryRunNeverSends() {
        configure(SysacadWriteMode.DRY_RUN, 5, 3, 30, List.of(Duration.ofMinutes(5)));
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.OK);
        verifyNoInteractions(sendCycle);
        verify(scheduleWriter).recordEvent(1L, ScheduleLogType.DRY_RUN_SEND, 5, 229, 1001L, null, null);
        verify(scheduleWriter).recordEvent(2L, ScheduleLogType.DRY_RUN_SEND, 5, 229, 1002L, null, null);
    }

    @Test
    @DisplayName("OFF no envía ni registra el envío simulado")
    void offDoesNotApply() {
        configure(SysacadWriteMode.OFF, 5, 3, 30, List.of(Duration.ofMinutes(5)));
        planReturns(send(schedule(1)));

        run.run(NOW);

        verifyNoInteractions(sendCycle);
        verify(scheduleWriter, never()).recordEvent(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Respeta el tope maxSendsPerRun y envía en el orden del plan, que ya viene por la clase más cercana")
    void respectsSendCapInPlanOrder() {
        configure(SysacadWriteMode.ON, 5, 3, 2, List.of(Duration.ofMinutes(5)));
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        SysacadSchedule third = schedule(3);
        planReturns(send(first), send(second), send(third));
        when(sendCycle.send(any(), any(), any())).thenReturn(outcome(Kind.CONFIRMED));

        run.run(NOW);

        InOrder order = inOrder(sendCycle);
        order.verify(sendCycle).send(eq(first), any(), any());
        order.verify(sendCycle).send(eq(second), any(), any());
        verify(sendCycle, never()).send(eq(third), any(), any());
    }

    @Test
    @DisplayName("Un horario con nextAttemptAt futuro se saltea y no consume el tope de envíos")
    void futureNextAttemptIsSkippedWithoutConsumingCap() {
        configure(SysacadWriteMode.ON, 5, 3, 1, List.of(Duration.ofMinutes(5)));
        SysacadSchedule waitingRetry = schedule(1);
        waitingRetry.setNextAttemptAt(NOW.plusSeconds(1));
        SysacadSchedule due = schedule(2);
        planReturns(send(waitingRetry), send(due));
        sendReturns(due, outcome(Kind.CONFIRMED));

        run.run(NOW);

        verify(sendCycle, never()).send(eq(waitingRetry), any(), any());
        verify(sendCycle).send(eq(due), any(), any());
    }

    @Test
    @DisplayName("Un horario con nextAttemptAt igual a ahora ya se envía")
    void nextAttemptEqualToNowIsSent() {
        SysacadSchedule due = schedule(1);
        due.setNextAttemptAt(NOW);
        planReturns(send(due));
        sendReturns(due, outcome(Kind.CONFIRMED));

        run.run(NOW);

        verify(sendCycle).send(eq(due), any(), any());
    }

    @Test
    @DisplayName("Con pausado_hasta en el futuro la corrida se saltea sin leer la vista ni planificar")
    void pausedUntilSkipsEverything() {
        state.setPausedUntil(NOW.plusSeconds(1));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.SKIPPED_PAUSED);
        verify(stateService, never()).startRun(any());
        verifyNoInteractions(viewReader, planner, sendCycle);
    }

    @Test
    @DisplayName("Con pausado_hasta igual a ahora la pausa terminó y la corrida sigue")
    void pausedUntilNowIsOver() {
        state.setPausedUntil(NOW);
        planReturns();

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.OK);
        verify(stateService).startRun(NOW);
    }

    @Test
    @DisplayName("No lee la vista si la última lectura fue hace menos de viewReadInterval")
    void doesNotReadViewBeforeInterval() {
        state.setLastViewReadAt(NOW.minus(Duration.ofHours(1)).plusSeconds(1));
        planReturns();

        run.run(NOW);

        verifyNoInteractions(viewReader);
        verify(stateService, never()).recordViewRead(any());
    }

    @Test
    @DisplayName("Lee la vista cuando pasó exactamente viewReadInterval y registra la lectura")
    void readsViewWhenIntervalElapsed() {
        state.setLastViewReadAt(NOW.minus(Duration.ofHours(1)));
        when(viewReader.read()).thenReturn(new ViewReadResult(List.of(), List.of()));
        planReturns();

        run.run(NOW);

        verify(viewReader).read();
        verify(stateService).recordViewRead(NOW);
    }

    @Test
    @DisplayName("Lee la vista si nunca se leyó")
    void readsViewWhenNeverRead() {
        state.setLastViewReadAt(null);
        when(viewReader.read()).thenReturn(new ViewReadResult(List.of(), List.of()));
        planReturns();

        run.run(NOW);

        verify(viewReader).read();
    }

    @Test
    @DisplayName("Si falla la lectura de la vista corta la corrida y marca la caída sin planificar")
    void viewReadFailureStopsAndMarksOutage() {
        state.setLastViewReadAt(null);
        when(viewReader.read()).thenThrow(new IllegalStateException("vista vacía"));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.VIEW_READ_FAILED);
        assertThat(report.errors()).singleElement().asString().contains("vista vacía");
        verify(stateService).markOutage(NOW);
        verify(stateService, never()).recordViewRead(any());
        verifyNoInteractions(planner, sendCycle);
        verify(stateService).finishRun(eq(NOW), eq("VIEW_READ_FAILED"), anyString());
    }

    @Test
    @DisplayName("Los cambios directos y simulados que devuelve la lectura de la vista pasan al handler")
    void viewReadResultsGoToHandler() {
        state.setLastViewReadAt(null);
        DryRunChange dryRun = new DryRunChange(8L, 5, 228, 5, 230);
        when(viewReader.read()).thenReturn(new ViewReadResult(List.of(4L, 5L), List.of(dryRun)));
        doThrow(new IllegalStateException("boom")).when(directChangeHandler).handle(4L);
        planReturns();

        RunReport report = run.run(NOW);

        verify(directChangeHandler).handle(5L);
        verify(directChangeHandler).recordDryRun(8L, 5, 228, 5, 230);
        assertThat(report.errors()).singleElement().asString().contains("horario 4");
    }

    @Test
    @DisplayName("Una decisión Waiting llama a toWaiting con la ocurrencia y el ítem")
    void waitingCallsToWaiting() {
        SysacadSchedule waiting = schedule(1);
        planReturns(new PlannedSchedule(waiting, new Waiting(1001L, 77L, CLASS_DATE, LocalTime.of(8, 0))));

        run.run(NOW);

        verify(scheduleWriter).toWaiting(1L, 1001L, 77L);
        verifyNoInteractions(sendCycle);
    }

    @Test
    @DisplayName("Un horario en WAITING que ya no está en el plan llama a clearWaiting y uno que sigue en el plan no")
    void staleWaitingIsCleared() {
        SysacadSchedule stale = schedule(9);
        SysacadSchedule stillWaiting = schedule(1);
        planReturns(new PlannedSchedule(stillWaiting, new Waiting(1001L, 77L, CLASS_DATE, LocalTime.of(8, 0))));
        when(scheduleRepository.findByStatus(SysacadScheduleStatus.WAITING)).thenReturn(List.of(stale, stillWaiting));

        run.run(NOW);

        verify(scheduleWriter).clearWaiting(9L);
        verify(scheduleWriter, never()).clearWaiting(1L);
    }

    @Test
    @DisplayName("K fallos seguidos: vuelca los fallos con la primera espera y sin sumar intento, corta y marca la caída")
    void consecutiveFailuresStopWithoutCountingAttempts() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        SysacadSchedule third = schedule(3);
        SysacadSchedule fourth = schedule(4);
        planReturns(send(first), send(second), send(third), send(fourth));
        when(sendCycle.send(any(), any(), any())).thenReturn(failed());

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.STOPPED_FAILURES);
        for (long id : new long[] { 1, 2, 3 }) {
            verify(scheduleWriter).recordFailure(id, "SERVER_ERROR", false, NOW.plus(Duration.ofMinutes(5)),
                    "HTTP 500", "cuerpo", "ERR-1");
        }
        verify(sendCycle, never()).send(eq(fourth), any(), any());
        verify(stateService).markOutage(NOW);
        verify(stateService, never()).clearOutage();
    }

    @Test
    @DisplayName("Un éxito entre fallos reinicia la racha y suma intentos a los fallos anteriores")
    void successResetsStreakAndCountsPreviousAttempts() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        SysacadSchedule third = schedule(3);
        SysacadSchedule fourth = schedule(4);
        SysacadSchedule fifth = schedule(5);
        planReturns(send(first), send(second), send(third), send(fourth), send(fifth));
        sendReturns(first, failed());
        sendReturns(second, failed());
        sendReturns(third, outcome(Kind.CONFIRMED));
        sendReturns(fourth, failed());
        sendReturns(fifth, failed());

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.OK);
        Instant firstRetry = NOW.plus(Duration.ofMinutes(5));
        verify(scheduleWriter).recordFailure(1L, "SERVER_ERROR", true, firstRetry, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter).recordFailure(2L, "SERVER_ERROR", true, firstRetry, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter).recordFailure(4L, "SERVER_ERROR", true, firstRetry, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter).recordFailure(5L, "SERVER_ERROR", true, firstRetry, "HTTP 500", "cuerpo", "ERR-1");
        verify(stateService, never()).markOutage(any());
    }

    @Test
    @DisplayName("Una racha de fallos seguida de STOP_CONNECTION no suma intentos")
    void streakFollowedByConnectionStopDoesNotCountAttempts() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        SysacadSchedule third = schedule(3);
        planReturns(send(first), send(second), send(third));
        sendReturns(first, failed());
        sendReturns(second, failed());
        sendReturns(third, outcome(Kind.STOP_CONNECTION));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.STOPPED_CONNECTION);
        Instant firstWait = NOW.plus(Duration.ofMinutes(5));
        verify(scheduleWriter).recordFailure(1L, "SERVER_ERROR", false, firstWait, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter).recordFailure(2L, "SERVER_ERROR", false, firstWait, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter, never()).recordFailure(anyLong(), anyString(), eq(true), any(), anyString(), any(),
                any());
        verify(stateService).markOutage(NOW);
    }

    @Test
    @DisplayName("Al llegar a maxAttempts el horario pasa a revisión MAX_ATTEMPTS y entra al reporte")
    void reachingMaxAttemptsOpensReview() {
        SysacadSchedule last = schedule(1);
        last.setAttempts(4);
        planReturns(send(last));
        sendReturns(last, failed());

        RunReport report = run.run(NOW);

        verify(scheduleWriter).recordFailure(1L, "SERVER_ERROR", true, null, "HTTP 500", "cuerpo", "ERR-1");
        verify(scheduleWriter).toReview(1L, SysacadReviewReason.MAX_ATTEMPTS, List.of());
        assertThat(report.reviewed()).containsExactly(
                new RunReport.ReviewedSchedule(1L, SysacadReviewReason.MAX_ATTEMPTS));
    }

    @Test
    @DisplayName("Un intento antes de maxAttempts no abre revisión")
    void oneBeforeMaxAttemptsStaysPending() {
        SysacadSchedule almost = schedule(1);
        almost.setAttempts(3);
        planReturns(send(almost));
        sendReturns(almost, failed());

        run.run(NOW);

        verify(scheduleWriter, never()).toReview(anyLong(), any(), any());
    }

    @ParameterizedTest(name = "con {0} intentos previos espera {1}")
    @CsvSource({
            "0, PT5M",
            "1, PT15M",
            "2, PT30M",
            "3, PT1H",
            "4, PT1H",
            "8, PT1H"
    })
    @DisplayName("La espera crece según retryBackoff y repite el último valor")
    void backoffGrowsAndRepeatsLastValue(int previousAttempts, Duration expectedWait) {
        configure(SysacadWriteMode.ON, 20, 3, 30, List.of(Duration.ofMinutes(5), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(1)));
        SysacadSchedule schedule = schedule(1);
        schedule.setAttempts(previousAttempts);
        planReturns(send(schedule));
        sendReturns(schedule, failed());

        run.run(NOW);

        verify(scheduleWriter).recordFailure(1L, "SERVER_ERROR", true, NOW.plus(expectedWait), "HTTP 500", "cuerpo",
                "ERR-1");
    }

    @Test
    @DisplayName("Una clase sin aula tras cancelar el pedido va al reporte y deja su fila")
    void noRoomAfterCancelGoesToReport() {
        SysacadSchedule schedule = schedule(1);
        planReturns(new PlannedSchedule(schedule, new NoRoomAfterCancel(1001L, 77L, CLASS_DATE)));

        RunReport report = run.run(NOW);

        assertThat(report.classesWithoutRoom())
                .containsExactly(new RunReport.ClassWithoutRoom(1L, 1001L, CLASS_DATE));
        verify(scheduleWriter).recordEvent(1L, ScheduleLogType.NO_ROOM_AFTER_CANCEL, null, null, 1001L, 77L, null);
        verifyNoInteractions(sendCycle);
    }

    @Test
    @DisplayName("Un destino no enviable deja su fila con el motivo y no se envía")
    void notSendableRecordsReason() {
        SysacadSchedule schedule = schedule(1);
        planReturns(new PlannedSchedule(schedule,
                new NotSendable(1001L, CLASS_DATE, DestinationNotSendableReason.CLASSROOM_NOT_FOUND)));

        run.run(NOW);

        verify(scheduleWriter).recordEvent(1L, ScheduleLogType.DESTINATION_NOT_SENDABLE, null, null, 1001L, null,
                DestinationNotSendableReason.CLASSROOM_NOT_FOUND.name());
        verifyNoInteractions(sendCycle);
    }

    @Test
    @DisplayName("Un fallo inesperado de un horario no corta los demás y queda en los errores del reporte")
    void unexpectedFailureDoesNotCutOthers() {
        SysacadSchedule broken = schedule(1);
        SysacadSchedule healthy = schedule(2);
        planReturns(send(broken), send(healthy));
        when(sendCycle.send(eq(broken), any(), any())).thenThrow(new IllegalStateException("explotó"));
        sendReturns(healthy, outcome(Kind.CONFIRMED));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.OK);
        assertThat(report.errors()).singleElement().asString().contains("Horario 1").contains("explotó");
        verify(sendCycle).send(eq(healthy), any(), any());
    }

    @Test
    @DisplayName("Si falla la planificación la corrida termina en ERROR y registra el fin")
    void plannerFailureEndsInError() {
        when(planner.plan(any())).thenThrow(new IllegalStateException("sin base"));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.ERROR);
        verify(stateService).finishRun(eq(NOW), eq("ERROR"), anyString());
    }

    @Test
    @DisplayName("STOP_RATE_LIMITED pausa hasta ahora más el Retry-After y corta")
    void rateLimitedPausesForRetryAfter() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        sendReturns(first, new SendOutcome(Kind.STOP_RATE_LIMITED, Duration.ofSeconds(90), null, null));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.STOPPED_RATE_LIMITED);
        verify(stateService).pauseUntil(NOW.plusSeconds(90));
        verify(sendCycle, never()).send(eq(second), any(), any());
    }

    @Test
    @DisplayName("STOP_RATE_LIMITED sin Retry-After pausa por jobDelay")
    void rateLimitedWithoutRetryAfterPausesForJobDelay() {
        SysacadSchedule first = schedule(1);
        planReturns(send(first));
        sendReturns(first, outcome(Kind.STOP_RATE_LIMITED));

        run.run(NOW);

        verify(stateService).pauseUntil(NOW.plus(properties.getWrite().jobDelay()));
    }

    @Test
    @DisplayName("STOP_UNAUTHORIZED marca la caída y corta")
    void unauthorizedMarksOutageAndStops() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        sendReturns(first, outcome(Kind.STOP_UNAUTHORIZED));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.STOPPED_UNAUTHORIZED);
        verify(stateService).markOutage(NOW);
        verify(sendCycle, never()).send(eq(second), any(), any());
    }

    @Test
    @DisplayName("STOP_HOURS_CHANGED corta la corrida y deja el horario en el reporte")
    void hoursChangedStopsRun() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        sendReturns(first, new SendOutcome(Kind.STOP_HOURS_CHANGED, null,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND, null));

        RunReport report = run.run(NOW);

        assertThat(report.result()).isEqualTo(Result.STOPPED_HOURS_CHANGED);
        assertThat(report.reviewed()).containsExactly(
                new RunReport.ReviewedSchedule(1L, SysacadReviewReason.HOURS_CHANGED_AFTER_SEND));
        verify(sendCycle, never()).send(eq(second), any(), any());
    }

    @Test
    @DisplayName("Un REVIEW del ciclo entra al reporte y la corrida sigue con el resto")
    void reviewOutcomeIsReportedAndRunContinues() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        sendReturns(first, new SendOutcome(Kind.REVIEW, null, SysacadReviewReason.HOUR_MISMATCH, null));
        sendReturns(second, outcome(Kind.CONFIRMED));

        RunReport report = run.run(NOW);

        assertThat(report.reviewed()).containsExactly(
                new RunReport.ReviewedSchedule(1L, SysacadReviewReason.HOUR_MISMATCH));
        verify(sendCycle).send(eq(second), any(), any());
    }

    @Test
    @DisplayName("Si se contactó a SysAcad con éxito limpia la caída y registra el fin de la corrida")
    void successfulContactClearsOutage() {
        SysacadSchedule first = schedule(1);
        planReturns(send(first));
        sendReturns(first, outcome(Kind.CONFIRMED));

        run.run(NOW);

        verify(stateService).startRun(NOW);
        verify(stateService).clearOutage();
        verify(stateService).finishRun(NOW, "OK", null);
    }

    @Test
    @DisplayName("Si no se contactó a SysAcad no limpia la caída")
    void noContactKeepsOutage() {
        planReturns();

        run.run(NOW);

        verify(stateService, never()).clearOutage();
    }

    @Test
    @DisplayName("El pacer de la corrida es el mismo para todos los envíos")
    void sameInstanceOfPacerForAllSends() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        org.mockito.ArgumentCaptor<RequestPacer> pacers = org.mockito.ArgumentCaptor.forClass(RequestPacer.class);
        when(sendCycle.send(any(), any(), pacers.capture())).thenReturn(outcome(Kind.CONFIRMED));

        run.run(NOW);

        assertThat(pacers.getAllValues()).hasSize(2).doesNotContainNull();
        assertThat(pacers.getAllValues().get(0)).isSameAs(pacers.getAllValues().get(1));
    }

    @Test
    @DisplayName("Con un horario en REVIEW por HOURS_CHANGED_AFTER_SEND devuelve SKIPPED_HOURS_CHANGED sin leer la vista ni enviar")
    void hoursChangedReviewSkipsTheRun() {
        when(scheduleRepository.existsByStatusAndReviewReason(SysacadScheduleStatus.REVIEW,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND)).thenReturn(true);

        RunReport report = run.run(NOW.plus(Duration.ofDays(1)));

        assertThat(report.result()).isEqualTo(Result.SKIPPED_HOURS_CHANGED);
        verifyNoInteractions(viewReader, planner, sendCycle);
        verify(stateService, never()).startRun(any());
        verify(stateService).finishRun(any(), eq(Result.SKIPPED_HOURS_CHANGED.name()), any());
    }

    @Test
    @DisplayName("Al resolver el horario en REVIEW por horas cambiadas, la corrida siguiente vuelve a enviar")
    void runSendsAgainOnceHoursChangedReviewIsResolved() {
        when(scheduleRepository.existsByStatusAndReviewReason(SysacadScheduleStatus.REVIEW,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND)).thenReturn(true, false);
        SysacadSchedule due = schedule(1);
        planReturns(send(due));
        sendReturns(due, outcome(Kind.CONFIRMED));

        RunReport blocked = run.run(NOW);
        verify(sendCycle, never()).send(any(), any(), any());
        RunReport resumed = run.run(NOW);

        assertThat(blocked.result()).isEqualTo(Result.SKIPPED_HOURS_CHANGED);
        assertThat(resumed.result()).isEqualTo(Result.OK);
        verify(sendCycle).send(eq(due), any(), any());
    }

    @Test
    @DisplayName("Una falla general de conexión deja la primera espera en los fallos pendientes, sin sumar intento")
    void generalFailureLeavesFirstWaitWithoutAttempt() {
        SysacadSchedule first = schedule(1);
        SysacadSchedule second = schedule(2);
        planReturns(send(first), send(second));
        sendReturns(first, failed());
        sendReturns(second, outcome(Kind.STOP_UNAUTHORIZED));

        run.run(NOW);

        verify(scheduleWriter).recordFailure(1L, "SERVER_ERROR", false, NOW.plus(Duration.ofMinutes(5)), "HTTP 500",
                "cuerpo", "ERR-1");
    }

    @Test
    @DisplayName("pauseUntil se calcula con el reloj y no con el instante con que se arrancó la corrida")
    void pauseUntilUsesClockInstant() {
        SysacadSchedule first = schedule(1);
        planReturns(send(first));
        sendReturns(first, new SendOutcome(Kind.STOP_RATE_LIMITED, Duration.ofSeconds(90), null, null));

        run.run(NOW.minus(Duration.ofMinutes(3)));

        verify(stateService).pauseUntil(NOW.plusSeconds(90));
    }
}
