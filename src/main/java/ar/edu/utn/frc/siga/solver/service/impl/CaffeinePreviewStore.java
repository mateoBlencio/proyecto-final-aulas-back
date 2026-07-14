package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.service.PreviewStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Implementación in-memory de {@link PreviewStore} sobre Caffeine, para una sola instancia.
 * Cada preview expira sola tras el TTL configurado ({@code siga.solver.preview.ttl-minutes}):
 * es un bound de staleness contra el estado de la BD, no una eviction por presión de memoria.
 */
@Component
public class CaffeinePreviewStore implements PreviewStore {

    private final Cache<String, SolverPreview> cache;

    public CaffeinePreviewStore(SolverProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.getPreview().getTtlMinutes()))
                .build();
    }

    @Override
    public void save(SolverPreview preview) {
        cache.put(preview.previewId(), preview);
    }

    @Override
    public Optional<SolverPreview> get(String previewId) {
        return Optional.ofNullable(cache.getIfPresent(previewId));
    }

    @Override
    public void remove(String previewId) {
        cache.invalidate(previewId);
    }
}
