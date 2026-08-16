package ar.edu.utn.frc.siga.settings.config;

import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsSeeder implements ApplicationRunner {

    private final SettingRepository repository;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        int seeded = 0;
        for (SettingKey key : SettingKey.values()) {
            if (!repository.existsById(key.getKey())) {
                repository.save(new Setting(key.getKey(), key.getDefaultValue()));
                seeded++;
            }
        }
        log.info("Seed de configuración completado: {} claves sembradas, {} ya existentes",
                seeded, SettingKey.values().length - seeded);
    }
}
