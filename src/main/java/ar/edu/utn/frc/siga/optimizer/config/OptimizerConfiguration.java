package ar.edu.utn.frc.siga.optimizer.config;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.ApplicationModuleListener;

@Slf4j
@Configuration
@EnableConfigurationProperties(OptimizerProperties.class)
@RequiredArgsConstructor
public class OptimizerConfiguration {

    private final SolverManagerProvider solverManagerProvider;

    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        SettingKey key = event.key();
        if (!"optimizer".equals(key.getCategory())
                || key == SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT) {
            return;
        }
        log.info("Cambió el setting {} del optimizador: reconstruyendo el SolverManager", key.getKey());
        solverManagerProvider.rebuild();
    }
}
