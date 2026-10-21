package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("SysacadSyncScheduler")
class SysacadSyncSchedulerTest {

    private final SysacadSyncOrchestrator orchestrator = mock(SysacadSyncOrchestrator.class);

    @Test
    @DisplayName("El cron dispara un resync completo, no vistas sueltas")
    void cronTriggersFullResync() {
        new SysacadSyncScheduler(orchestrator).resyncAll();

        verify(orchestrator).resyncAll();
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    @DisplayName("Hay un único método programado")
    void hasSingleScheduledMethod() {
        long scheduled = java.util.Arrays.stream(SysacadSyncScheduler.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .count();

        assertThat(scheduled).isEqualTo(1);
    }
}
