package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SysacadSyncScheduler")
class SysacadSyncSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");

    private final SysacadSyncOrchestrator orchestrator = mock(SysacadSyncOrchestrator.class);
    private final SysacadRuntimeSettings runtimeSettings = mock(SysacadRuntimeSettings.class);
    private final ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
    private final ScheduledFuture<?> first = mock(ScheduledFuture.class);
    private final ScheduledFuture<?> second = mock(ScheduledFuture.class);
    private SysacadSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(runtimeSettings.syncCron()).thenReturn("0 0 3 * * SUN");
        org.mockito.Mockito.doReturn(first, second).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        scheduler = new SysacadSyncScheduler(orchestrator, runtimeSettings, ZONE, taskScheduler);
    }

    @Test
    @DisplayName("El cron dispara un resync completo, no vistas sueltas")
    void cronTriggersFullResync() {
        scheduler.resyncAll();

        verify(orchestrator).resyncAll();
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    @DisplayName("start agenda con un CronTrigger armado desde el cron y la zona configurados")
    void startSchedulesCronFromSettings() {
        scheduler.start();

        Trigger trigger = capturedTrigger(1);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger).getExpression()).isEqualTo("0 0 3 * * SUN");
        verify(taskScheduler).initialize();
    }

    @Test
    @DisplayName("El trigger cae el domingo a las 03:00 en la zona configurada")
    void triggerFiresOnConfiguredDayAndHour() {
        scheduler.start();

        Instant next = capturedTrigger(1).nextExecution(new SimpleTriggerContext(
                java.time.Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZONE)));

        ZonedDateTime local = next.atZone(ZONE);
        assertThat(local.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(local.getHour()).isEqualTo(3);
        assertThat(local.getMinute()).isZero();
        assertThat(next.atZone(ZoneId.of("UTC")).getHour()).as("03:00 en Córdoba es 06:00 UTC").isEqualTo(6);
    }

    @Test
    @DisplayName("Cambiar el día o la hora cancela el agendado anterior y agenda de nuevo con el cron actual")
    void dayOrTimeChangeReschedules() {
        scheduler.start();
        when(runtimeSettings.syncCron()).thenReturn("0 30 14 * * WED");

        scheduler.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_SYNC_DAY_OF_WEEK));

        verify(first).cancel(false);
        assertThat(((CronTrigger) capturedTrigger(2)).getExpression()).isEqualTo("0 30 14 * * WED");

        scheduler.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_SYNC_TIME));

        verify(second).cancel(false);
        verify(taskScheduler, times(3)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("Un cambio de otra clave no reprograma")
    void otherKeyDoesNotReschedule() {
        scheduler.start();

        scheduler.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_WRITE_MODE));
        scheduler.onSettingChanged(new SettingChangedEvent(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES));

        verify(first, never()).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("stop apaga el scheduler")
    void stopShutsDownScheduler() {
        scheduler.stop();

        verify(taskScheduler).shutdown();
    }

    private Trigger capturedTrigger(int expectedCalls) {
        ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(expectedCalls)).schedule(any(Runnable.class), captor.capture());
        return captor.getAllValues().getLast();
    }
}
