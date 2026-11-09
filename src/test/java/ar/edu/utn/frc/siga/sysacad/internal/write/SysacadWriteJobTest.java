package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.SysacadTestSettings;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadConflictException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncLock;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadWriteStateService;
import ar.edu.utn.frc.siga.sysacad.internal.write.review.SysacadAlertService;

@DisplayName("SysacadWriteJob")
class SysacadWriteJobTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant PREVIOUS_END = Instant.parse("2026-03-10T11:55:00Z");
    private static final RunReport OK = new RunReport(Result.OK, List.of(), List.of(), List.of());

    private final SysacadWriteRun run = mock(SysacadWriteRun.class);
    private final SysacadWriteStateService stateService = mock(SysacadWriteStateService.class);
    private final SysacadAlertService alertService = mock(SysacadAlertService.class);
    private final SysacadSyncLock syncLock = new SysacadSyncLock();
    private SysacadRuntimeSettings runtimeSettings;
    private SysacadWriteJob job;

    @BeforeEach
    void stubState() {
        SysacadWriteState state = new SysacadWriteState();
        state.setLastRunFinishedAt(PREVIOUS_END);
        when(stateService.current()).thenReturn(state);
    }

    private SysacadWriteJob jobIn(SysacadWriteMode mode) {
        runtimeSettings = SysacadTestSettings.with(mode);
        when(runtimeSettings.jobDelay()).thenReturn(Duration.ofMillis(20));
        job = new SysacadWriteJob(run, stateService, alertService, syncLock, runtimeSettings,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return job;
    }

    @AfterEach
    void stopJob() {
        if (job != null) {
            job.stop();
        }
    }

    @Test
    @DisplayName("Con el modo OFF agenda el job pero no corre nada")
    void offSchedulesButDoesNotRun() throws InterruptedException {
        jobIn(SysacadWriteMode.OFF).start();

        assertThat(job.isRunning()).isTrue();
        Thread.sleep(100);
        verify(run, never()).run(any());
    }

    @Test
    @DisplayName("Con el modo ON agenda la corrida con la hora del reloj y stop la cancela")
    void onSchedulesRunsAndStopCancels() {
        when(run.run(NOW)).thenReturn(OK);

        jobIn(SysacadWriteMode.ON).start();

        assertThat(job.isRunning()).isTrue();
        verify(run, timeout(2000).atLeastOnce()).run(NOW);
        job.stop();
        assertThat(job.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Con el modo SANDBOX también agenda la corrida")
    void sandboxAlsoSchedules() {
        when(run.run(NOW)).thenReturn(OK);

        jobIn(SysacadWriteMode.SANDBOX).start();

        assertThat(job.isRunning()).isTrue();
        verify(run, timeout(2000).atLeastOnce()).run(NOW);
    }

    @Test
    @DisplayName("Arrancar dos veces no crea un segundo executor")
    void startTwiceKeepsSingleExecutor() {
        when(run.run(NOW)).thenReturn(OK);
        jobIn(SysacadWriteMode.ON).start();

        job.start();
        job.stop();

        assertThat(job.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Con el lock del sync tomado saltea la corrida y corre cuando se libera")
    void skipsWhileSyncHoldsLock() throws InterruptedException {
        when(run.run(NOW)).thenReturn(OK);
        syncLock.lock();
        try {
            jobIn(SysacadWriteMode.ON).start();
            Thread.sleep(200);
            verify(run, never()).run(any());
        } finally {
            syncLock.unlock();
        }

        verify(run, timeout(2000).atLeastOnce()).run(NOW);
    }

    @Test
    @DisplayName("Una excepción de la corrida no mata el executor: la siguiente corre y el lock queda suelto")
    void exceptionDoesNotKillExecutorAndReleasesLock() throws InterruptedException {
        when(run.run(NOW)).thenThrow(new IllegalStateException("explotó")).thenReturn(OK);

        jobIn(SysacadWriteMode.ON).start();

        verify(run, timeout(2000).atLeast(2)).run(NOW);
        job.stop();
        Thread.sleep(50);
        assertThat(syncLock.tryLock()).as("el lock no quedó tomado por el hilo del job").isTrue();
        syncLock.unlock();
    }

    @Test
    @DisplayName("Un Error de la corrida tampoco mata el executor ni deja el lock tomado")
    void errorDoesNotKillExecutor() throws InterruptedException {
        when(run.run(NOW)).thenThrow(new StackOverflowError()).thenReturn(OK);

        jobIn(SysacadWriteMode.ON).start();

        verify(run, timeout(2000).atLeast(2)).run(NOW);
        job.stop();
        Thread.sleep(50);
        assertThat(syncLock.tryLock()).isTrue();
        syncLock.unlock();
    }

    @Test
    @DisplayName("El job no publica beans: ningún método @Bean, así no mueve los @Scheduled de la app a su hilo")
    void jobDeclaresNoBeans() {
        long beanMethods = java.util.Arrays.stream(SysacadWriteJob.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .count();

        assertThat(beanMethods).isZero();
    }

    @Test
    @DisplayName("Tras la corrida avisa a Subsecretaría con el fin de la corrida anterior, leído antes de correr")
    void alertsWithPreviousRunEnd() {
        when(run.run(NOW)).thenReturn(OK);

        jobIn(SysacadWriteMode.ON).start();

        verify(alertService, timeout(2000).atLeastOnce()).alert(PREVIOUS_END);
    }

    @Test
    @DisplayName("Un fallo del aviso no se propaga: el lock queda suelto y la siguiente corrida sigue")
    void alertFailureDoesNotBreakTheJob() throws InterruptedException {
        when(run.run(NOW)).thenReturn(OK);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp caído")).when(alertService).alert(any());

        jobIn(SysacadWriteMode.ON).start();

        verify(run, timeout(2000).atLeast(2)).run(NOW);
        job.stop();
        Thread.sleep(50);
        assertThat(syncLock.tryLock()).isTrue();
        syncLock.unlock();
    }

    @Test
    @DisplayName("Cambiar el delay del job reinicia el executor con el delay nuevo")
    void delayChangeRestartsExecutorWithNewDelay() throws InterruptedException {
        when(run.run(NOW)).thenReturn(OK);
        jobIn(SysacadWriteMode.ON);
        when(runtimeSettings.jobDelay()).thenReturn(Duration.ofHours(1));
        job.start();
        Thread.sleep(100);
        verify(run, never()).run(any());

        when(runtimeSettings.jobDelay()).thenReturn(Duration.ofMillis(20));
        job.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES));

        assertThat(job.isRunning()).isTrue();
        verify(run, timeout(2000).atLeastOnce()).run(NOW);
    }

    @Test
    @DisplayName("Un cambio de otra clave no reinicia el executor")
    void otherKeyDoesNotRestartExecutor() throws InterruptedException {
        when(run.run(NOW)).thenReturn(OK);
        jobIn(SysacadWriteMode.ON);
        when(runtimeSettings.jobDelay()).thenReturn(Duration.ofHours(1));
        job.start();

        when(runtimeSettings.jobDelay()).thenReturn(Duration.ofMillis(20));
        job.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_WRITE_MODE));

        Thread.sleep(150);
        verify(run, never()).run(any());
        verify(runtimeSettings, org.mockito.Mockito.times(1)).jobDelay();
    }

    @Test
    @DisplayName("Un cambio de delay sin el job iniciado no arranca nada")
    void delayChangeWithoutStartedJobDoesNotStart() {
        jobIn(SysacadWriteMode.ON);

        job.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES));

        assertThat(job.isRunning()).isFalse();
        verify(runtimeSettings, never()).jobDelay();
    }

    @Test
    @DisplayName("runNow con el modo OFF lanza conflicto y no corre")
    void runNowInOffThrowsConflict() {
        jobIn(SysacadWriteMode.OFF);

        assertThatThrownBy(() -> job.runNow()).isInstanceOf(SysacadConflictException.class);
        verify(run, never()).run(any());
    }

    @Test
    @DisplayName("runNow con el lock tomado por otro hilo lanza conflicto y no corre")
    void runNowWithLockHeldElsewhereThrowsConflict() throws InterruptedException {
        jobIn(SysacadWriteMode.ON);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            syncLock.lock();
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                syncLock.unlock();
            }
        });
        holder.start();
        assertThat(held.await(2, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> job.runNow()).isInstanceOf(SysacadConflictException.class);
            verify(run, never()).run(any());
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    @Test
    @DisplayName("runNow en ON devuelve el reporte de la corrida y suelta el lock")
    void runNowReturnsReportAndReleasesLock() {
        when(run.run(NOW)).thenReturn(OK);
        jobIn(SysacadWriteMode.ON);

        RunReport report = job.runNow();

        assertThat(report).isSameAs(OK);
        verify(alertService).alert(PREVIOUS_END);
        assertThat(syncLock.tryLock()).isTrue();
        syncLock.unlock();
    }

    @Test
    @DisplayName("runNow en SANDBOX también corre")
    void runNowInSandboxRuns() {
        when(run.run(NOW)).thenReturn(OK);
        jobIn(SysacadWriteMode.SANDBOX);

        assertThat(job.runNow()).isSameAs(OK);
    }
}
