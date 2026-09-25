package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.preview.service.ReallocationSuggestion;
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
    private volatile Cache<String, ReallocationSuggestion> suggestionCache;

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

    @Override
    public void saveSuggestion(ReallocationSuggestion suggestion) {
        suggestionCache.put(suggestion.suggestionId(), suggestion);
    }

    @Override
    public Optional<ReallocationSuggestion> takeSuggestion(String suggestionId) {
        // asMap().remove es atómico: dos confirms concurrentes con el mismo id nunca reciben los dos la sugerencia.
        return Optional.ofNullable(suggestionCache.asMap().remove(suggestionId));
    }

    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        if (event.key() != SettingKey.PREVIEW_TTL_MINUTES) {
            return;
        }
        log.info("Cambió el TTL de las previews: reconstruyendo el cache (se descartan las previews y sugerencias activas)");
        rebuild();
    }

    private void rebuild() {
        Duration ttl = Duration.ofMinutes(previewSettings.getTtlMinutes());
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
        this.suggestionCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }
}
