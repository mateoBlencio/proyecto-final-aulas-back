package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

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

    @Test
    @DisplayName("El cron de application.yaml es válido y cae el domingo a las 3 en la zona configurada")
    void configuredCronFiresSundayAtThree() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        Binder binder = new Binder(new MapConfigurationPropertySource(
                (Map<String, Object>) ((MapPropertySource) sources.get(0)).getSource()));
        String cron = binder.bind("siga.sysacad.sync.cron", String.class).get();
        ZoneId zone = ZoneId.of(binder.bind("siga.sysacad.sync.zone", String.class).get());

        assertThat(CronExpression.isValidExpression(cron)).isTrue();
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, zone));
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 11, 15, 3, 0, 0, 0, zone));
    }
}
