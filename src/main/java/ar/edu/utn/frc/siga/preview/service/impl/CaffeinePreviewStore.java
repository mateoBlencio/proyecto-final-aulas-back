package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaffeinePreviewStore implements PreviewStore {

    private final PreviewSettings previewSettings;

    private volatile Cache<String, OptimizationResult> cache;

    @PostConstruct
    void init() {
        rebuild();
    }

    @Override
    public void save(OptimizationResult preview) {
        cache.put(preview.previewId(), preview);
    }

    @Override
    public Optional<OptimizationResult> get(String previewId) {
        return Optional.ofNullable(cache.getIfPresent(previewId));
    }

    @Override
    public void remove(String previewId) {
        cache.invalidate(previewId);
    }

    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        if (event.key() != SettingKey.PREVIEW_TTL_MINUTES) {
            return;
        }
        log.info("Cambió el TTL de las previews: reconstruyendo el cache (se descartan las previews activas)");
        rebuild();
    }

    private void rebuild() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(previewSettings.getTtlMinutes()))
                .build();
    }
}
