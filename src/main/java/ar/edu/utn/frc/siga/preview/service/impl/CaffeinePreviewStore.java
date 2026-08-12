package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.preview.config.PreviewProperties;
import ar.edu.utn.frc.siga.preview.service.PreviewStore;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class CaffeinePreviewStore implements PreviewStore {

    private final Cache<String, OptimizationResult> cache;

    public CaffeinePreviewStore(PreviewProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.getTtlMinutes()))
                .build();
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
}
