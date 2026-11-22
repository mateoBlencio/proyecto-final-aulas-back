package ar.edu.utn.frc.siga.common.config;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/** Hay 8 {@code @Scheduled}: con el pool por defecto (1 hilo) una tarea lenta atrasa a las demás. */
@DisplayName("Pool del scheduler (integracion)")
class SchedulingPoolIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private Environment environment;

    @Test
    @DisplayName("el TaskScheduler de Boot tiene 4 hilos")
    void taskSchedulerHasFourThreads() {
        assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(((ThreadPoolTaskScheduler) taskScheduler).getScheduledThreadPoolExecutor().getCorePoolSize())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("spring.task.scheduling.pool.size resuelve a 4")
    void poolSizePropertyResolvesToFour() {
        assertThat(environment.getProperty("spring.task.scheduling.pool.size", Integer.class)).isEqualTo(4);
    }
}
