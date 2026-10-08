package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RoomRequestSuggestionStore {

    private static final long MAX_SUGGESTIONS = 1_000;

    private final SettingsReader settingsReader;

    private Cache<String, RoomRequestSuggestion> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl())
                .maximumSize(MAX_SUGGESTIONS)
                .build();
    }

    // A diferencia de reconstruir el cache, cambiar el TTL en el lugar conserva las sugerencias vigentes.
    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        if (event.key() == SettingKey.PREVIEW_TTL_MINUTES) {
            cache.policy().expireAfterWrite().orElseThrow().setExpiresAfter(ttl());
        }
    }

    private Duration ttl() {
        return Duration.ofMinutes(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES));
    }

    void save(RoomRequestSuggestion suggestion) {
        cache.put(suggestion.suggestionId(), suggestion);
    }

    // asMap().remove es atómico: dos confirms concurrentes con el mismo id nunca reciben los dos la sugerencia.
    Optional<RoomRequestSuggestion> take(String suggestionId) {
        return Optional.ofNullable(cache.asMap().remove(suggestionId));
    }
}
